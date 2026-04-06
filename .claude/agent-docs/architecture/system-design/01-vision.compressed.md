<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/system-design/01-vision.md -->

# RepCheck System Design

## Vision
RepCheck helps users understand legislator voting alignment via:
1. **Dynamic Q&A** — Users answer questionnaire on political topics
2. **User Political Profiles** — Responses → structured preference profiles
3. **Bill Intelligence** — LLM analysis produces: summaries, topic tags, stance, pork/rider detection, impact, fiscal estimates
4. **Voting Record Tracking** — All congress member votes tracked
5. **Alignment Scoring** — LLM compares user profiles vs legislator voting records → alignment scores
6. **Pre-computed Results** — Batch-computed scores cached in AlloyDB for instant retrieval

## Core Architecture

### Data Model

**Users Table**
```sql
CREATE TABLE users (
  id STRING PRIMARY KEY,
  created_at TIMESTAMP,
  political_profile JSON,
  last_updated TIMESTAMP
);
```

**Bills Table**
```sql
CREATE TABLE bills (
  id STRING PRIMARY KEY,
  congress INT64,
  bill_number STRING,
  title STRING,
  summary STRING,
  topics ARRAY<STRING>,
  stance STRING,
  has_pork BOOL,
  has_riders BOOL,
  impact_analysis STRING,
  fiscal_estimate STRING,
  text_url STRING,
  introduced_date DATE,
  processed_at TIMESTAMP
);
```

**Votes Table**
```sql
CREATE TABLE votes (
  id STRING PRIMARY KEY,
  bill_id STRING,
  member_id STRING,
  vote STRING, -- 'yes'|'no'|'abstain'
  voted_at TIMESTAMP
);
```

**Alignment Scores Table**
```sql
CREATE TABLE alignment_scores (
  id STRING PRIMARY KEY,
  user_id STRING,
  member_id STRING,
  score FLOAT64, -- 0.0-1.0
  computed_at TIMESTAMP,
  bills_analyzed INT64
);
```

### Questionnaire Service

When To Use:
- User onboarding
- Profile refresh
- A/B testing question variations

```python
class QuestionnaireService:
    def __init__(self, llm_client, db):
        self.llm = llm_client
        self.db = db
    
    def generate_questions(self, num_questions=10):
        # Generate timely political questions via LLM
        prompt = f"Generate {num_questions} non-partisan questions on current policy topics"
        questions = self.llm.generate(prompt)
        return self._validate_and_store(questions)
    
    def process_responses(self, user_id, answers):
        # Transform responses → political profile JSON
        profile_prompt = f"Analyze these policy answers: {answers}\nProduce structured political profile"
        profile = self.llm.generate(profile_prompt)
        self.db.users.update({'id': user_id}, {'political_profile': profile})
        return profile
```

### Bill Processing Pipeline

When To Use:
- New bills introduced
- Regular batch processing (daily/weekly)
- Bill text updates

```python
class BillProcessingService:
    def __init__(self, llm_client, db):
        self.llm = llm_client
        self.db = db
    
    def process_bill(self, bill_text, metadata):
        # LLM: extract summary, topics, stance, pork/riders, impact, fiscal estimate
        extraction_prompt = f"""
        Analyze this bill text:
        {bill_text}
        
        Output JSON:
        {{
            "summary": "...",
            "topics": ["healthcare", "tax", ...],
            "stance": "progressive|conservative|neutral",
            "has_pork": bool,
            "has_riders": bool,
            "impact_analysis": "...",
            "fiscal_estimate": "$X billion"
        }}
        """
        analysis = self.llm.generate(extraction_prompt)
        bill_doc = {**metadata, **analysis}
        self.db.bills.insert(bill_doc)
        return bill_doc
    
    def batch_process_congress(self):
        # Fetch new bills, process each via LLM, store results
        new_bills = self._fetch_new_bills()
        for bill in new_bills:
            self.process_bill(bill['text'], bill['metadata'])
```

### Alignment Scoring Engine

When To Use:
- User submits questionnaire
- Legislator voting pattern changes
- Batch pre-computation (nightly)

```python
class AlignmentScoringService:
    def __init__(self, llm_client, db):
        self.llm = llm_client
        self.db = db
    
    def score_legislator(self, user_id, member_id):
        # Retrieve user profile and member voting record
        user = self.db.users.get(user_id)
        votes = self.db.votes.query({'member_id': member_id})
        bills = [self.db.bills.get(v['bill_id']) for v in votes]
        
        # LLM: compare profile against voting pattern
        scoring_prompt = f"""
        User political profile:
        {user['political_profile']}
        
        Legislator votes:
        {[(b['stance'], v['vote']) for b, v in zip(bills, votes)]}
        
        Score alignment 0.0-1.0 where:
        - 1.0 = perfect alignment
        - 0.0 = complete opposition
        Return JSON: {{"score": float}}
        """
        result = self.llm.generate(scoring_prompt)
        score_doc = {
            'user_id': user_id,
            'member_id': member_id,
            'score': result['score'],
            'computed_at': now(),
            'bills_analyzed': len(bills)
        }
        self.db.alignment_scores.insert(score_doc)
        return score_doc
    
    def batch_compute_all_scores(self):
        # Nightly: compute all user-legislator alignment pairs
        users = self.db.users.list()
        members = self.db.members.list()
        for user in users:
            for member in members:
                self.score_legislator(user['id'], member['id'])
```

### API Layer

When To Use:
- User-facing endpoints
- Frontend integration
- Mobile app support

```python
# FastAPI endpoints
@app.post("/api/questionnaire/start")
def start_questionnaire(user_id: str):
    service = QuestionnaireService(llm, db)
    questions = service.generate_questions()
    return {"user_id": user_id, "questions": questions}

@app.post("/api/questionnaire/submit")
def submit_questionnaire(user_id: str, answers: list):
    service = QuestionnaireService(llm, db)
    profile = service.process_responses(user_id, answers)
    return {"user_id": user_id, "profile": profile}

@app.get("/api/alignment/{user_id}/{member_id}")
def get_alignment_score(user_id: str, member_id: str):
    # Retrieve pre-computed score from AlloyDB
    score = db.alignment_scores.query({
        'user_id': user_id,
        'member_id': member_id
    })
    return {"user_id": user_id, "member_id": member_id, "score": score['score']}

@app.get("/api/legislator/{member_id}/votes")
def get_legislator_votes(member_id: str, limit: int = 20):
    votes = db.votes.query({'member_id': member_id}, limit=limit)
    bills = [db.bills.get(v['bill_id']) for v in votes]
    return {"member_id": member_id, "recent_votes": list(zip(votes, bills))}
```

## How to Create

### 1. Database Setup (AlloyDB PostgreSQL)
- Create tables: users, bills, votes, alignment_scores
- Add indexes on: user_id, member_id, bill_id, computed_at
- Enable JSON columns for political_profile
- Configure replication for disaster recovery

### 2. LLM Integration
- Initialize LLM client (OpenAI/Claude/Gemini) with system prompts
- Define extraction templates for bill analysis
- Cache frequent prompts via embedding index
- Implement retry logic + fallback models

### 3. Bill Data Pipeline
- Integrate Congress.gov API or ProPublica Congress API to fetch new bills
- Store raw bill text in GCS with versioning
- Schedule daily batch processing via Cloud Tasks
- Monitor LLM token usage and costs

### 4. Questionnaire Generation
- Create dynamic question templates covering: healthcare, taxes, environment, defense, social issues
- Use LLM to rephrase questions for clarity and non-partisanship
- A/B test question sets for response quality
- Store question metadata (topic, difficulty, pass rate)

### 5. Batch Scoring Jobs
- Deploy nightly Cloud Run job to compute all alignment scores
- Batch LLM API calls (1000+ scores per run)
- Update AlloyDB with computed results
- Cache results in Redis for <100ms retrieval

### 6. Frontend/API
- Build REST API with FastAPI
- Add authentication (Firebase/Auth0)
- Implement caching headers (ETags, Cache-Control)
- Add rate limiting per user
- Expose bill details, legislator profiles, voting records

## Performance Considerations

| Operation | Latency Target | Strategy |
|-----------|----------------|----------|
| Get alignment score | <100ms | AlloyDB cache + Redis |
| Generate new questionnaire | <2s | LLM batch cached prompts |
| Process new bill | <30s | Async Cloud Tasks, no user wait |
| Search legislator votes | <500ms | AlloyDB indexed queries |
| Compute all scores (nightly) | <4 hrs | Parallel batch processing |

## Monitoring & Alerts

- LLM API error rate > 5% → escalate
- Alignment score computation > 6 hrs → page on-call
- Stale bill data (>7 days) → warning
- User questionnaire completion <30% → review questions