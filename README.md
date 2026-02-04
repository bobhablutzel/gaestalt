# Geastalt

Mono-repo for interconnected systems. Each system is an independent project with its own build, deployment, and Docker context.

## Systems

| System | Description | Status |
|--------|-------------|--------|
| [member](member/) | Member management service with gRPC/REST APIs and Kafka consumers | Active |
| [lock](lock/) | Distributed lock manager with Raft consensus and cross-region quorum | Active |

## Structure

```
geastalt/
├── member/           # Member management system
│   ├── member-api/
│   ├── member-common/
│   ├── member-consumer-ids/
│   ├── member-consumer-address/
│   ├── helm/
│   ├── k8s/
│   └── terraform/
├── lock/             # Distributed lock manager
│   ├── src/
│   ├── helm/
│   └── Dockerfile
└── (future systems)
```

## Design Principles

- **Independent builds**: Each system has its own parent POM and builds independently
- **Independent deployments**: Each system produces its own Docker images and Kubernetes resources
- **Shared nothing**: Systems communicate via APIs and messaging, not shared libraries
- **Docker context per system**: Each system's Dockerfile context is its own directory

## Architecture

```mermaid
block
  columns 3
  owner["Geastalt Business Architecture"]:3
  block:appGroup:2
    columns 2
    Mobile Web Desktop API
  end
  app["Application Layer"]
  block:domainGroup:2
    columns 2
    Member Address Contract
  end
  domain["Domain Layer"]
  block:foundGroup:2
    columns 2
    Lock Notification 
  end
  foundation["Foundation Layer"]
  block:infraGroup:2
    columns 2
    Kubernetes Docker SQL Queue 
  end
  infra["Infrastructure Layer"]


style infra fill:#127,stroke:#333;
style infraGroup fill:#127,stroke:#333;
style foundation fill:#522,stroke:#333;
style foundGroup fill:#522,stroke:#333;
style domain fill:#634,stroke:#333,color:#fff;
style domainGroup fill:#634,stroke:#333;
style app fill:#942,stroke:#333,color:#fff;
style appGroup fill:#942,stroke:#333;
style owner fill:#C43,color:#000,stroke-width:0,font-size:70px;
```
