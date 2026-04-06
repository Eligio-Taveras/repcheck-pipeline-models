<!-- GENERATED FILE — DO NOT EDIT. Source: docs/architecture/system-design/03-event-flow.md -->

# Event Flow (Pub/Sub)

```mermaid
sequenceDiagram
    participant Scheduler as Cloud Scheduler
    participant BillPipe as Bill Pipeline
    participant VotePipe as Vote Pipeline
    participant MemberPipe as Member Pipeline
    participant AmendPipe as Amendment Pipeline
    participant PubSub as Pub/Sub
    participant Analysis as Bill Analysis (LLM)
    participant Scoring as Alignment Scoring (LLM)
    participant DB as AlloyDB

    Scheduler->>BillPipe: Trigger (cron)
    Scheduler->>VotePipe: Trigger (cron)
    Scheduler->>MemberPipe: Trigger (cron)
    Scheduler->>AmendPipe: Trigger (cron)

    BillPipe->>DB: Store bills
    BillPipe->>PubSub: bill.text.available

    VotePipe->>DB: Store votes
    VotePipe->>PubSub: vote.recorded

    MemberPipe->>DB: Store members
    AmendPipe->>DB: Store amendments

    PubSub->>Analysis: bill.text.available
    Analysis->>Analysis: Fetch bill text
    Analysis->>Analysis: Call LLM (structured JSON)
    Analysis->>DB: Store analysis result + embeddings
    Analysis->>PubSub: analysis.completed

    PubSub->>Scoring: analysis.completed / vote.recorded
    Scoring->>DB: Read bill analyses + votes + user profiles
    Scoring->>Scoring: Call LLM for alignment scoring (pgvector similarity)
    Scoring->>DB: Write pre-computed scores
```

**Pattern:** Cloud Scheduler triggers four pipelines on schedule. Bill/Vote/Member/Amendment pipelines store data in AlloyDB and publish events to Pub/Sub. Bill Analysis subscribes to `bill.text.available`, fetches text, calls LLM for structured analysis, stores results + embeddings, publishes `analysis.completed`. Alignment Scoring subscribes to `analysis.completed` and `vote.recorded`, reads stored analyses/votes/profiles, calls LLM for similarity-based scoring using pgvector, writes pre-computed scores to DB.