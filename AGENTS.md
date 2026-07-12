# AGENTS.md instructions

## Project Direction

This project is a learning-oriented reproduction of Youzan's Transparent Multilevel Cache (TMC) design.

Reference article:

- https://tech.youzan.com/tmc/

All future development, architecture decisions, documentation, tests, demos, and benchmarks should be based on the ideas and flow described in that article. The project should reproduce the article's core chain as faithfully as practical:

1. Transparent Jedis-style client integration.
2. Client-side hot key detection support and local cache.
3. Access event reporting through rsyslog + Kafka.
4. Server-side hot key detection with sliding windows.
5. Hot key publishing through etcd.
6. Local cache invalidation and cross-node invalidation broadcast.
7. Metrics, demo, and benchmark evidence showing reduced Redis pressure and increased local cache hit rate.

When implementation details are not explicitly specified by the article, prefer choices that preserve the article's architecture and learning value over shortcuts that hide the core design.

Architecture rule:

- Do not create a Java `interface` for a component that is expected to have only one implementation. Use the concrete class directly.
- Introduce an `interface` only when it represents a real extension boundary with multiple expected runtime implementations.

Code comment rule:

- All production code must include readable, complete comments that help a new maintainer understand the component without relying on chat history.
- Every Java class should have a class-level comment explaining its responsibility, where it sits in the TMC chain, and any important design constraints.
- Important fields, configuration properties, public methods, scheduled tasks, background threads, queues, network I/O, cache behavior, and concurrency boundaries must be commented.
- Comments should explain intent, data flow, failure behavior, and non-obvious tradeoffs. Do not write empty line-by-line narration such as "set value" or "return result".
- Test classes should include comments describing the behavior being protected, especially when the test documents a design decision or edge case.
- When adding or changing code, update nearby comments so they remain accurate.

<!-- CODEGRAPH_START -->
## CodeGraph

This project has a CodeGraph MCP server (`codegraph_*` tools) configured. CodeGraph is a tree-sitter-parsed knowledge graph of every symbol, edge, and file. Reads are sub-millisecond and return structural information grep cannot.

### When to prefer codegraph over native search

Use codegraph for **structural** questions — what calls what, what would break, where is X defined, what is X's signature. Use native grep/read only for **literal text** queries (string contents, comments, log messages) or after you already have a specific file open.

| Question | Tool |
|---|---|
| "Where is X defined?" / "Find symbol named X" | `codegraph_search` |
| "What calls function Y?" | `codegraph_callers` |
| "What does Y call?" | `codegraph_callees` |
| "How does X reach/become Y? / trace the flow from X to Y" | `codegraph_trace` |
| "What would break if I changed Z?" | `codegraph_impact` |
| "Show me Y's signature / source / docstring" | `codegraph_node` |
| "Give me focused context for a task/area" | `codegraph_context` |
| "See several related symbols' source at once" | `codegraph_explore` |
| "What files exist under path/" | `codegraph_files` |
| "Is the index healthy?" | `codegraph_status` |

### Rules of thumb

- **Answer directly — don't delegate exploration.** For "how does X work" / architecture questions, answer with 2-3 codegraph calls: `codegraph_context` first, then ONE `codegraph_explore` for the source of the symbols it surfaces. For a specific **flow** ("how does X reach Y") start with `codegraph_trace` from to — one call returns the whole path with dynamic hops bridged — then ONE `codegraph_explore` for the bodies; don't rebuild the path with `codegraph_search` + `codegraph_callers`. CodeGraph IS the pre-built index, so spawning a separate file-reading sub-task/agent — or running a grep + read loop — repeats work codegraph already did and costs more for the same answer.
- **Trust codegraph results.** They come from a full AST parse. Do NOT re-verify them with grep — that's slower, less accurate, and wastes context.
- **Don't grep first** when looking up a symbol by name. `codegraph_search` is faster and returns kind + location + signature in one call.
- **Don't chain `codegraph_search` + `codegraph_node`** when you just want context — `codegraph_context` is one call.
- **Don't loop `codegraph_node` over many symbols** — one `codegraph_explore` call returns several symbols' source grouped in a single capped call, while each separate node/Read call re-reads the whole context and costs far more.
- **Index lag — check the staleness banner, don't guess a wait.** When a codegraph response starts with "Some files referenced below were edited since the last index sync", the listed files are pending re-index — Read those specific files for accurate content. Files NOT in that banner are fresh and codegraph is authoritative for them. `codegraph_status` also lists pending files under "Pending sync".

### If `.codegraph/` doesn't exist

The MCP server returns "not initialized." Ask the user: *"I notice this project doesn't have CodeGraph initialized. Want me to run `codegraph init -i` to build the index?"*
<!-- CODEGRAPH_END -->
