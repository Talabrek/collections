# Features Research: Web Admin Panel

**Domain:** Web admin panel for Minecraft plugin configuration (Collections plugin)
**Researched:** 2026-01-23
**Confidence:** MEDIUM (verified against multiple admin panels, industry patterns, CMS best practices)

## Table Stakes

Features users expect from any web admin panel. Missing these makes the product feel incomplete.

### Authentication & Security

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Token/API key authentication | Server admins need secure access without exposing panel to everyone | Medium | JWT or API key approach; penetration-tested panels like McMyAdmin set the bar |
| Role-based permissions | Multiple admins need different access levels | Medium | At minimum: read-only, editor, admin |
| HTTPS support | Security baseline for any web admin tool | Low | Certificate handling for production |

**Source:** [McMyAdmin](https://mcmyadmin.com/) is the only panel to undergo independent CREST penetration testing.

### Core CRUD Operations

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Create new collections | Primary use case | Medium | Form-based with validation |
| Edit existing collections | Primary use case | Medium | Load, modify, save workflow |
| Delete collections | Basic data management | Low | With confirmation |
| Duplicate collections | Time saver for similar content | Low | Clone with new ID |
| View collection list | Navigation foundation | Low | Sortable, searchable table |

### File Management

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| View all YAML files | Need to see what exists | Low | File browser style |
| Validate YAML syntax | Prevent broken configs | Low | Real-time validation on edit |
| Syntax error reporting | Users need to know what's wrong | Low | Line numbers, clear messages |
| Backup before save | Safety net for mistakes | Low | Auto-backup on edit |

**Source:** [PaperMC Plugin Configurations](https://docs.papermc.io/paper/dev/plugin-configurations/) documents YAML as the standard config format.

### User Experience Basics

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Responsive design | Admin work from mobile/tablet | Medium | Modern panels (Pterodactyl, MCSManager) all responsive |
| Clear navigation | Users need to find features | Low | Sidebar pattern is standard |
| Loading states | Feedback during operations | Low | Spinners, progress indicators |
| Error handling | Users need to know when things fail | Low | Toast notifications, error messages |
| Save confirmation | Users need to know changes are saved | Low | Visual feedback on save |

**Source:** [Pterodactyl](https://pterodactyl.io/) sets the modern standard for game panel UI with React-based responsive interface.

## Visual Builder Features

What a drag-drop collection builder specifically needs.

### Item Browser/Selector

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Searchable Minecraft item list | ~1,300+ items in 1.21 need filtering | Medium | Use Minecraft item registry data |
| Item preview (icon/texture) | Visual recognition faster than text | Medium | Render item sprites or use CDN |
| Filter by category | Items organized by type (blocks, tools, etc.) | Low | Standard Minecraft categories |
| Filter by material type | Quick narrowing of choices | Low | Search by material enum |
| Recent items | Speed up repetitive tasks | Low | Track last N selected |

**Source:** [Minecraft Item IDs](https://minecraftitemids.com/) demonstrates searchable item databases with 1,325+ items.

### Drag-and-Drop Patterns

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Visual drag affordances | Users need to know what's draggable | Low | Grab handles, cursor changes |
| Ghost image while dragging | Maintain visual context | Low | Standard DnD pattern |
| Drop zone highlighting | Show valid drop targets | Low | Border/background change on hover |
| Reorder items in list | Adjust item order | Medium | Sortable list pattern |
| Drag from item browser to collection | Add items workflow | Medium | Cross-container drag |

**Source:** [Pencil & Paper UX Patterns](https://www.pencilandpaper.io/articles/ux-pattern-drag-and-drop) - "Items need to visually say 'Yes, you can pick me up' via drag handles, shadow on hover, or cursor change."

### Form Fields for Collection Data

Based on the existing collection YAML structure:

| Field | Type | Complexity | Notes |
|-------|------|------------|-------|
| id | Text input with validation | Low | Unique, lowercase, underscore format |
| name | Text input | Low | Display name with MiniMessage preview |
| description | Textarea | Low | With MiniMessage preview |
| tier | Dropdown | Low | COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, EVENT |
| conditions.biomes | Multi-select | Medium | Minecraft biome enum list |
| conditions.dimensions | Multi-select | Low | NORMAL, NETHER, THE_END |
| conditions.min-y / max-y | Number inputs | Low | Y-level range |
| items[] | Item list builder | High | The visual builder core |
| rewards | Reward builder | Medium | Experience, message, fireworks, items |

### Item Entry Form

For each item in the collection:

| Field | Type | Complexity | Notes |
|-------|------|------------|-------|
| item_id | Text input | Low | Internal ID (snake_case) |
| name | Text input | Low | Display name |
| material | Item picker | Medium | Minecraft material search/select |
| lore[] | Text list | Medium | MiniMessage formatted lines |
| weight | Number slider | Low | Drop weight percentage |

## Differentiators

Features that would make this stand out from basic config editors.

### Live Preview (HIGH VALUE)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| MiniMessage preview | See formatted text as players will | Medium | Parse MiniMessage to HTML/canvas |
| Item tooltip preview | See item appearance before saving | Medium | Render like Minecraft tooltip |
| Collection card preview | See GUI appearance | High | Mock the in-game GUI rendering |

**Source:** [Strapi 5](https://strapi.io/blog/headless-cms-visual-editor-features) - "Live Preview renders content in an iframe next to the editing form with real-time updates as you type."

### Smart Defaults & Templates

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Collection templates | Start from biome-appropriate defaults | Low | Pre-built templates for forest, ocean, nether, etc. |
| Auto-generate collection ID | Derive from name | Low | Quality of life |
| Suggested weights | Balance item drop rates | Medium | Sum to 100, warn on imbalance |
| Biome-aware item suggestions | Suggest thematically appropriate items | Medium | Based on biome selection |

### Validation & Safety

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Duplicate ID detection | Prevent broken configs | Low | Cross-file validation |
| Material validation | Ensure valid Minecraft materials | Low | Check against enum |
| Missing field warnings | Catch incomplete collections | Low | Required field highlighting |
| Weight sum validation | Ensure weights add to 100 | Low | Real-time calculation |
| Biome conflict detection | Warn on overlapping zones | Medium | Cross-zone analysis |

### Server Integration

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Hot reload trigger | Apply changes without restart | Medium | Call plugin reload command via RCON or API |
| Connection status indicator | Know if panel is connected to server | Low | Heartbeat/ping |
| Server logs viewer | Debug issues from panel | Medium | Tail log files |

### Undo/History

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Undo/redo stack | Recover from mistakes | Medium | Track edit history |
| Version history | Compare changes over time | High | Git-like versioning |
| Restore previous version | Rollback bad changes | High | Requires version storage |

## Anti-Features

Features to deliberately NOT build for v1.3 scope.

### Over-Engineering

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Real-time collaborative editing | Massive complexity (CRDT/OT), unlikely use case for server configs | Single-editor model with lock warnings |
| WebSocket live sync | Overkill for config editing; adds infrastructure complexity | Polling on page load, manual refresh |
| Offline-first/PWA | Config editing requires server connection anyway | Simple web app, require connectivity |
| Multi-server dashboard | v1.3 focus is single server; network support is separate milestone | Single server UI |

### Scope Creep

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Player data management | Already have admin commands; web UI adds security risk | Keep in-game commands |
| Live server map integration | Massive scope, separate product | Zone list without map visualization |
| Spawn preview on world map | Requires world rendering infrastructure | Text-based coordinate display |
| Full plugin settings editor | config.yml is simpler, risk of breaking things | Focus on collections/zones only |
| Chat/console integration | Security risk, already have RCON tools | Out of scope |

### Premature Optimization

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Database backend for panel | YAML files are the source of truth | Read/write YAML directly |
| Caching layer | File system is fast enough for config editing | Direct file access |
| CDN for item sprites | Complexity for small asset set | Bundle sprites or use existing CDN |
| Micro-frontend architecture | Over-engineering for single-purpose tool | Monolithic React/Vue app |

### Security Overreach

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| User account system | Adds complexity; API keys simpler | API key authentication |
| OAuth/SSO integration | Enterprise feature for v2+ | Simple token auth |
| Audit logging | Nice to have but not MVP | File backups serve similar purpose |
| IP whitelisting UI | Server-level concern | Document firewall setup |

## Reference Examples

### Game Server Panels (UI/UX Reference)

| Panel | Strengths | Relevance |
|-------|-----------|-----------|
| [Pterodactyl](https://pterodactyl.io/) | Modern React UI, clean design, Docker integration | UI patterns, responsive design |
| [MCSManager](https://www.mcsmanager.com/) | Multi-server management, distributed architecture | Dashboard layout |
| [Crafty Controller](https://craftycontrol.com/) | Player management, backup scheduling | Feature scoping |
| [Multicraft](https://www.multicraft.org/) | Plugin management, FTP integration | Plugin config patterns |

### Visual Builders (Drag-Drop Reference)

| Tool | Strengths | Relevance |
|------|-----------|-----------|
| [Retool](https://retool.com/) | Drag-drop component builder, database integration | Builder UI patterns |
| Strapi Content Builder | Field types, validation, preview | Form building patterns |
| [Salesforce Content Builder](https://email.uplers.com/blog/content-builder-versus-classic-content-in-salesforce-marketing-cloud-exploring-the-differences/) | Drag-drop interface, real-time preview, autosave | Visual editing patterns |

### YAML Editors (Technical Reference)

| Tool | Strengths | Relevance |
|------|-----------|-----------|
| [Google YAML UI Editor](https://github.com/google/yaml-ui-editor) | Schema-driven forms, Git integration | JSON Schema approach |
| [YamlWebEditor](https://github.com/gsilos/YamlWebEditor) | Collaborative editing, Jenkins integration | Team workflows |
| [Visual File Editor (Spigot)](https://polymart.org/resource/visual-file-editor.2) | In-game YAML editing | Minecraft-specific patterns |

### Minecraft Item Databases (Data Reference)

| Source | Strengths | Relevance |
|--------|-----------|-----------|
| [Minecraft Item IDs](https://minecraftitemids.com/) | Searchable, 1,325+ items, card/table views | Item browser UI |
| [MCUtils Item IDs](https://mcutils.com/item-ids) | Legacy + current IDs, developer-focused | Data completeness |

## Recommended Feature Prioritization for v1.3

### Phase 1: Foundation
1. API key authentication
2. Collection CRUD (create, read, update, delete)
3. YAML validation
4. Basic form fields for all collection properties

### Phase 2: Visual Builder
5. Item browser with search
6. Drag-drop item management
7. Material selector with icons
8. MiniMessage preview

### Phase 3: Polish
9. Collection templates
10. Zone CRUD
11. Validation warnings (duplicates, missing fields)
12. Hot reload trigger

### Defer to v1.4+
- Version history
- Multi-server support
- Player data management web UI
- Map visualization

## Sources

- [Pterodactyl Panel](https://pterodactyl.io/) - Modern game server panel UI reference
- [MCSManager](https://www.mcsmanager.com/) - Distributed game server management
- [Crafty Controller](https://craftycontrol.com/) - Minecraft server management
- [McMyAdmin](https://mcmyadmin.com/) - Security-focused Minecraft panel
- [Pencil & Paper DnD UX](https://www.pencilandpaper.io/articles/ux-pattern-drag-and-drop) - Drag-drop design patterns
- [LogRocket DnD Design](https://blog.logrocket.com/ux-design/drag-and-drop-ui-examples/) - DnD UI best practices
- [Strapi Visual Editor](https://strapi.io/blog/headless-cms-visual-editor-features) - CMS preview features
- [Minecraft Item IDs](https://minecraftitemids.com/) - Item database reference
- [Google YAML UI Editor](https://github.com/google/yaml-ui-editor) - Schema-driven YAML editing
- [Visual File Editor](https://polymart.org/resource/visual-file-editor.2) - Minecraft YAML editor plugin
- [PaperMC Docs](https://docs.papermc.io/paper/dev/plugin-configurations/) - Plugin configuration standards
- [Medium: Autosaving Patterns](https://medium.com/@brooklyndippo/to-save-or-to-autosave-autosaving-patterns-in-modern-web-applications-39c26061aa6b) - Save/sync strategies
