# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label string from this table.

Edit the right-hand column to match whatever vocabulary you actually use.

## Label palette

MattSkillsDeck renders label colors in the local-markdown backend. This table is the single source of truth for those colors — one label per row. Custom labels are added as new rows here.

| Label | Color | Meaning |
| --- | --- | --- |
| wayfinder:map | #8b5cf6 | The map issue of a wayfinder effort |
| wayfinder:research | #0ea5e9 | Research ticket (AFK) |
| wayfinder:prototype | #f59e0b | Prototype ticket (HITL) |
| wayfinder:grilling | #9d7cd8 | Grilling / discussion ticket (HITL) |
| wayfinder:task | #10b981 | Task ticket (HITL or AFK) |
| bug | #d73a4a | Something is broken (fix action / BUG filter) |
| needs-triage | #fbca04 | Unexamined issue awaiting diagnosis |
| needs-info | #5319e7 | Waiting on reporter for more information |
| ready-for-agent | #0e8a16 | Fully specified, ready for an AFK agent |
| ready-for-human | #b60205 | Requires human implementation |
| wontfix | #ffffff | Will not be actioned |

> ⚠️ Do not delete this table or its rows — deleting a row breaks the color feature (labels not listed here lose their color).