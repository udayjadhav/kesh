(() => {
    const form = document.getElementById("composer");
    const prompt = document.getElementById("prompt");
    const thread = document.getElementById("thread");
    const send = document.getElementById("send");
    const sidebar = document.getElementById("sidebar");
    const recent = document.getElementById("recent");
    const hideSidebar = document.getElementById("hide-sidebar");
    const showSidebar = document.getElementById("show-sidebar");
    const newChat = document.getElementById("new-chat");
    const toolList = document.getElementById("tool-list");
    const composerShell = document.querySelector(".composer-shell");
    const expandComposer = document.getElementById("expand-composer");
    const scrollTopBtn = document.getElementById("scroll-top");
    const scrollBottomBtn = document.getElementById("scroll-bottom");
    const jumpLatest = document.getElementById("jump-latest");
    const chatStage = document.querySelector(".chat-stage");
    const SIDEBAR_KEY = "kesh.sidebarHidden";
    const DESK = (document.body.getAttribute("data-desk") || "work").toLowerCase();
    const API_BASE = "/" + DESK + "/api";
    const CONVO_KEY = "kesh." + DESK + ".conversationId";

    let conversationId = sessionStorage.getItem(CONVO_KEY);
    let sending = false;
    let stickToBottom = true;
    let debugMode = new URLSearchParams(window.location.search).get("debug") === "1";

    setSidebarHidden(localStorage.getItem(SIDEBAR_KEY) === "1");
    loadTools();
    loadConfig();
    if (conversationId) {
        refreshRecent().then(() => openChat(conversationId));
    } else {
        conversationId = crypto.randomUUID();
        sessionStorage.setItem(CONVO_KEY, conversationId);
        refreshRecent();
    }

    hideSidebar.addEventListener("click", () => setSidebarHidden(true));
    showSidebar.addEventListener("click", () => setSidebarHidden(false));
    newChat.addEventListener("click", () => startNewChat());
    wireComposer();
    wireThreadScroll();
    syncEmptyState();
    resizePrompt();

    document.querySelectorAll(".copy-cmd").forEach((button) => {
        button.addEventListener("click", async () => {
            const command = button.getAttribute("data-cmd") || "";
            try {
                await navigator.clipboard.writeText(command);
            } catch {
                // clipboard may be blocked; still fill the box
            }
            prompt.value = command;
            prompt.focus();
            resizePrompt();
            document.getElementById("commands-menu").open = false;
        });
    });

    document.querySelectorAll("details.menu").forEach((menu) => {
        menu.addEventListener("toggle", () => {
            if (menu.open) {
                document.querySelectorAll("details.menu").forEach((other) => {
                    if (other !== menu) {
                        other.open = false;
                    }
                });
            }
        });
    });

    prompt.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            form.requestSubmit();
        }
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const message = prompt.value.trim();
        if (!message || sending) {
            return;
        }
        prompt.value = "";
        setComposerExpanded(false);
        resizePrompt();
        stickToBottom = true;
        await sendMessage(message);
    });

    async function sendMessage(message) {
        sending = true;
        send.disabled = true;
        addBubble("you", "You", message);
        const assistant = addBubble("assistant", "Kesh", "");
        const status = document.createElement("div");
        status.className = "status";
        status.setAttribute("aria-live", "polite");
        showBusyStatus(status, "Thinking", "thinking");
        const body = assistant.querySelector(".md-body");
        assistant.insertBefore(status, body);
        try {
            await consume(API_BASE + "/chat/stream", { conversationId, message }, assistant, status, body);
            await refreshRecent();
        } catch (error) {
            clearStatus(status);
            body.classList.add("draft");
            body.textContent = error.message || "Request failed";
            maybeStick();
        } finally {
            sending = false;
            send.disabled = false;
            prompt.focus();
            syncScrollUi();
        }
    }

    async function consume(url, payload, assistant, status, body) {
        const response = await fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json", Accept: "text/event-stream" },
            body: JSON.stringify(payload)
        });
        if (!response.ok || !response.body) {
            throw new Error("Kesh could not reach the agent (" + response.status + ")");
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = "";
        while (true) {
            const { value, done } = await reader.read();
            if (done) {
                break;
            }
            buffer += decoder.decode(value, { stream: true });
            const parts = buffer.split("\n\n");
            buffer = parts.pop();
            for (const part of parts) {
                handleSseBlock(part, assistant, status, body);
            }
        }
        if (buffer.trim()) {
            handleSseBlock(buffer, assistant, status, body);
        }
        if (status.dataset.mode === "busy" && status.dataset.kind === "thinking") {
            clearStatus(status);
        }
    }

    function handleSseBlock(block, assistant, status, body) {
        const lines = block.split("\n");
        let eventName = "message";
        let data = "";
        for (const line of lines) {
            if (line.startsWith("event:")) {
                eventName = line.slice(6).trim();
            } else if (line.startsWith("data:")) {
                data += line.slice(5).trim();
            }
        }
        if (!data) {
            return;
        }
        let payload = {};
        try {
            payload = JSON.parse(data);
        } catch {
            payload = { text: data, message: data };
        }
        if (eventName === "thinking") {
            showBusyStatus(status, "Thinking", "thinking");
        } else if (eventName === "nlp") {
            showBusyStatus(status, routingLabel(payload), "understanding");
            if (debugMode) {
                renderDebug(assistant, payload);
            }
        } else if (eventName === "routing") {
            showBusyStatus(status, routingLabel(payload), "routing");
        } else if (eventName === "context") {
            const n = (payload.sources || []).length;
            showBusyStatus(status, n ? "Building context · " + n + " sources" : "Building context", "context");
        } else if (eventName === "token") {
            showBusyStatus(status, "Generating", "generating");
            const next = (body.dataset.raw || "") + (payload.text || "");
            if (looksLikeToolLeak(next)) {
                return;
            }
            body.dataset.raw = next;
            assistant.dataset.raw = next;
            body.classList.add("draft");
            body.textContent = next;
            maybeStick();
        } else if (eventName === "tool_start") {
            const chip = document.createElement("div");
            chip.className = "tool-chip";
            chip.textContent = toolStartLabel(payload);
            assistant.insertBefore(chip, body);
            maybeStick();
        } else if (eventName === "tool_result") {
            const chip = document.createElement("div");
            chip.className = "tool-chip" + (payload.success ? "" : " fail");
            chip.textContent = toolResultLabel(payload);
            assistant.insertBefore(chip, body);
            if (payload.sources && payload.sources.length) {
                assistant._sources = (assistant._sources || []).concat(payload.sources);
            }
            maybeStick();
        } else if (eventName === "approval_required") {
            clearStatus(status);
            renderApproval(assistant, payload, status, body);
        } else if (eventName === "done") {
            clearStatus(status);
            if (payload.conversationId) {
                conversationId = payload.conversationId;
                sessionStorage.setItem(CONVO_KEY, conversationId);
            }
            const raw = stripToolLeak((payload.message && payload.message.trim())
                ? payload.message
                : (body.dataset.raw || body.textContent || ""));
            paintMarkdown(body, raw);
            assistant.dataset.raw = raw;
            if (payload.writing) {
                body.replaceChildren();
                renderWriting(assistant, payload.writing);
            }
            renderSources(assistant, payload.sources || assistant._sources || []);
            renderVotes(assistant, payload);
        } else if (eventName === "error") {
            clearStatus(status);
            body.classList.add("draft");
            body.textContent = payload.message || "Something went wrong";
            maybeStick();
        }
    }

    function routingLabel(payload) {
        const capability = payload.capability || (payload.classification && payload.classification.intent) || "";
        const domain = payload.domain || (payload.classification && payload.classification.domain) || "";
        if (domain || capability) {
            return "Understanding request · " + (domain || "CHAT") + (capability ? " / " + capability : "");
        }
        if (payload.steps && payload.steps.length) {
            return "Routing · " + payload.steps.map((step) => step.intent || step.domain).join(" + ");
        }
        return "Understanding request";
    }

    function toolStartLabel(payload) {
        const tool = payload.tool || "";
        const args = payload.arguments || {};
        if (tool === "web_search") {
            return "🔎 Searching web… " + (args.query || "");
        }
        if (tool === "web_fetch") {
            return "📄 Reading " + (args.url || "");
        }
        if (tool === "web_crawl") {
            return "🕸 Crawling " + (args.url || "");
        }
        return "→ " + tool;
    }

    function toolResultLabel(payload) {
        const tool = payload.tool || "";
        if (!payload.success) {
            return "✗ " + tool;
        }
        if (tool === "web_search") {
            const match = String(payload.output || "").match(/Found (\d+) sources/);
            return match ? "✓ Found " + match[1] + " sources" : "✓ Search complete";
        }
        if (tool === "web_fetch") {
            return "✓ Page read";
        }
        if (tool === "web_crawl") {
            const match = String(payload.output || "").match(/Crawled (\d+) pages/);
            return match ? "✓ Crawled " + match[1] + " pages" : "✓ Crawl complete";
        }
        return "✓ " + tool;
    }

    function renderSources(assistant, sources) {
        const previous = assistant.querySelector(".sources");
        if (previous) {
            previous.remove();
        }
        const unique = [];
        const seen = new Set();
        (sources || []).forEach((source) => {
            const url = source && source.url;
            if (!url || seen.has(url)) {
                return;
            }
            seen.add(url);
            unique.push(source);
        });
        if (!unique.length) {
            return;
        }
        const box = document.createElement("div");
        box.className = "sources";
        const heading = document.createElement("div");
        heading.className = "sources-title";
        heading.textContent = "Sources";
        box.appendChild(heading);
        unique.forEach((source) => {
            const item = document.createElement("details");
            item.className = "source-item";
            const summary = document.createElement("summary");
            summary.className = "source-summary";
            const link = document.createElement("a");
            link.className = "source-link";
            link.href = source.url;
            link.target = "_blank";
            link.rel = "noopener noreferrer";
            link.textContent = source.title || source.source || source.url;
            link.addEventListener("click", (event) => event.stopPropagation());
            summary.appendChild(link);
            item.appendChild(summary);
            const body = document.createElement("div");
            body.className = "source-body";
            const urlLine = document.createElement("a");
            urlLine.className = "source-url";
            urlLine.href = source.url;
            urlLine.target = "_blank";
            urlLine.rel = "noopener noreferrer";
            urlLine.textContent = source.url;
            body.appendChild(urlLine);
            if (source.snippet) {
                const snippet = document.createElement("p");
                snippet.className = "source-snippet";
                snippet.textContent = source.snippet;
                body.appendChild(snippet);
            }
            item.appendChild(body);
            box.appendChild(item);
        });
        assistant.appendChild(box);
    }

    function renderApproval(assistant, payload, status, body) {
        const box = document.createElement("div");
        box.className = "approval";
        const title = document.createElement("strong");
        title.textContent = "AI wants to execute:";
        const code = document.createElement("code");
        code.textContent = payload.tool || "";
        const pre = document.createElement("pre");
        pre.textContent = JSON.stringify(payload.arguments || {}, null, 2);
        const actions = document.createElement("div");
        const approve = document.createElement("button");
        approve.type = "button";
        approve.className = "approve";
        approve.textContent = "Approve";
        const reject = document.createElement("button");
        reject.type = "button";
        reject.className = "secondary reject";
        reject.textContent = "Reject";
        actions.append(approve, reject);
        box.append(title, code, pre, actions);
        assistant.insertBefore(box, body);
        maybeStick();
        approve.addEventListener("click", async () => {
            box.remove();
            showBusyStatus(status, "Running tool", "thinking");
            await consume(API_BASE + "/approvals/" + payload.id + "/approve/stream", {}, assistant, status, body);
            await refreshRecent();
        });
        reject.addEventListener("click", async () => {
            box.remove();
            showBusyStatus(status, "Rejected — thinking", "thinking");
            await consume(API_BASE + "/approvals/" + payload.id + "/reject/stream", {}, assistant, status, body);
            await refreshRecent();
        });
    }

    function renderVotes(assistant, payload) {
        const previous = assistant.querySelector(".vote-row");
        if (previous) {
            previous.remove();
        }
        const interactionId = payload && payload.interactionId;
        if (!interactionId) {
            return;
        }
        const row = document.createElement("div");
        row.className = "vote-row";
        const up = document.createElement("button");
        up.type = "button";
        up.className = "ghost vote vote-up";
        up.setAttribute("aria-label", "Thumbs up");
        up.title = "Thumbs up";
        up.textContent = "👍";
        const down = document.createElement("button");
        down.type = "button";
        down.className = "ghost vote vote-down";
        down.setAttribute("aria-label", "Thumbs down");
        down.title = "Thumbs down";
        down.textContent = "👎";
        const comment = document.createElement("input");
        comment.type = "text";
        comment.className = "vote-comment";
        comment.placeholder = "Optional comment";
        comment.maxLength = 400;
        comment.setAttribute("aria-label", "Optional comment");
        const note = document.createElement("span");
        note.className = "vote-note";
        row.append(up, down, comment, note);
        assistant.appendChild(row);
        async function send(type, reason, button) {
            try {
                await fetch(API_BASE + "/feedback", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        interactionId,
                        conversationId,
                        type,
                        reason,
                        comment: comment.value.trim()
                    })
                });
                up.disabled = true;
                down.disabled = true;
                comment.disabled = true;
                button.classList.add("selected");
                note.textContent = "";
            } catch (error) {
                note.textContent = "Could not save";
            }
        }
        up.addEventListener("click", () => send("POSITIVE", "HELPFUL", up));
        down.addEventListener("click", () => send("NEGATIVE", "INCORRECT_INTENT", down));
    }

    function renderWriting(assistant, writing) {
        const previous = assistant.querySelector(".writing-card");
        if (previous) {
            previous.remove();
        }
        if (!writing) {
            return;
        }
        const card = document.createElement("div");
        card.className = "writing-card";
        const mode = writing.mode === "CORRECTION" ? "CORRECT"
            : writing.mode === "FORMAT" ? "FORMAT"
            : "REWRITE";
        const heading = document.createElement("div");
        heading.className = "writing-mode";
        heading.textContent = mode + (writing.label ? " · " + writing.label : "");
        card.appendChild(heading);

        function block(title, text) {
            const wrap = document.createElement("div");
            wrap.className = "writing-block";
            const label = document.createElement("div");
            label.className = "writing-label";
            label.textContent = title;
            const pre = document.createElement("pre");
            pre.className = "writing-text";
            pre.textContent = text || "";
            wrap.append(label, pre);
            return wrap;
        }
        card.appendChild(block("Original", writing.original || ""));
        card.appendChild(block(writing.label || "Result", writing.output || ""));

        if (Array.isArray(writing.diff) && writing.diff.length) {
            const details = document.createElement("details");
            details.className = "writing-diff";
            const summary = document.createElement("summary");
            summary.textContent = "Show diff";
            const pre = document.createElement("p");
            pre.className = "writing-diff-body";
            writing.diff.forEach((token) => {
                const span = document.createElement("span");
                span.className = "diff-" + (token.type || "equal");
                span.textContent = (token.text || "") + " ";
                pre.appendChild(span);
            });
            details.append(summary, pre);
            card.appendChild(details);
        }

        const primary = document.createElement("div");
        primary.className = "writing-actions writing-primary";
        [
            ["Copy", (btn) => copyText(writing.output || "", btn)],
            ["Edit", () => {
                prompt.value = writing.output || "";
                prompt.focus();
            }],
            ["Regenerate", () => {
                const instruction = writing.instruction || "format this message";
                const source = writing.original || "";
                prompt.value = source ? (source + "\n\n" + instruction) : instruction;
                form.requestSubmit();
            }],
            ["Accept", (btn) => {
                copyText(writing.output || "", btn);
                const up = assistant.querySelector(".vote-up");
                if (up) {
                    up.click();
                }
            }]
        ].forEach(([label, handler]) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "ghost writing-chip";
            button.textContent = label;
            button.addEventListener("click", () => handler(button));
            primary.appendChild(button);
        });
        card.appendChild(primary);

        const actions = document.createElement("div");
        actions.className = "writing-actions";
        ["Too formal", "Too verbose", "Too robotic", "Changed my meaning", "Make it shorter", "More natural"].forEach((reason) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "ghost writing-chip";
            button.textContent = reason;
            button.addEventListener("click", () => {
                const row = assistant.querySelector(".vote-row");
                const comment = row && row.querySelector(".vote-comment");
                if (comment) {
                    comment.value = reason;
                }
                const down = row && row.querySelector(".vote-down");
                if (down) {
                    down.click();
                }
            });
            actions.appendChild(button);
        });
        card.appendChild(actions);
        assistant.appendChild(card);
        maybeStick();
    }

    function addBubble(kind, who, text, options) {
        const bubble = document.createElement("div");
        const isUser = kind === "you" || kind === "user";
        const quiet = options && options.scroll === false;
        bubble.className = "bubble " + (isUser ? "user" : "assistant");
        const head = document.createElement("div");
        head.className = "who-row";
        const label = document.createElement("span");
        label.className = "who";
        label.textContent = who;
        head.appendChild(label);
        if (!isUser) {
            const copy = document.createElement("button");
            copy.type = "button";
            copy.className = "ghost copy-reply";
            copy.textContent = "Copy reply";
            copy.addEventListener("click", () => copyText(bubble.dataset.raw || "", copy));
            head.appendChild(copy);
        }
        bubble.appendChild(head);
        if (isUser) {
            const p = document.createElement("p");
            p.textContent = text || "";
            bubble.appendChild(p);
        } else {
            const body = document.createElement("div");
            body.className = "md-body";
            bubble.appendChild(body);
            if (text) {
                paintMarkdown(body, text, quiet);
                bubble.dataset.raw = text;
            }
        }
        thread.appendChild(bubble);
        syncEmptyState();
        if (!quiet) {
            maybeStick();
        }
        return bubble;
    }

    function startNewChat() {
        conversationId = crypto.randomUUID();
        sessionStorage.setItem(CONVO_KEY, conversationId);
        thread.replaceChildren();
        stickToBottom = true;
        markActive();
        syncEmptyState();
        syncScrollUi();
        prompt.focus();
        resizePrompt();
    }

    async function openChat(id) {
        const response = await fetch(API_BASE + "/conversations/" + id);
        if (!response.ok) {
            startNewChat();
            return;
        }
        const data = await response.json();
        conversationId = data.id;
        sessionStorage.setItem(CONVO_KEY, conversationId);
        thread.replaceChildren();
        (data.messages || []).forEach((message) => {
            const who = message.role === "user" ? "You" : "Kesh";
            addBubble(message.role, who, message.content, { scroll: false });
        });
        markActive();
        stickToBottom = true;
        setComposerExpanded(false);
        resizePrompt();
        syncEmptyState();
        scrollThreadTo("bottom", false);
        syncScrollUi();
    }

    async function refreshRecent() {
        const response = await fetch(API_BASE + "/conversations");
        if (!response.ok) {
            return;
        }
        const chats = await response.json();
        recent.replaceChildren();
        chats.forEach((chat) => {
            const button = document.createElement("button");
            button.type = "button";
            button.textContent = chat.title;
            button.dataset.id = chat.id;
            if (chat.id === conversationId) {
                button.classList.add("active");
            }
            button.addEventListener("click", () => openChat(chat.id));
            recent.appendChild(button);
        });
    }

    function markActive() {
        recent.querySelectorAll("button").forEach((button) => {
            button.classList.toggle("active", button.dataset.id === conversationId);
        });
    }

    async function loadConfig() {
        try {
            const response = await fetch(API_BASE + "/config");
            if (!response.ok) {
                return;
            }
            const config = await response.json();
            if (config.debugEnabled) {
                debugMode = true;
            }
        } catch {
            // config is optional
        }
    }

    function looksLikeToolLeak(text) {
        return /<(web_search|web_fetch|web_crawl|tool_call)\b/i.test(text || "");
    }

    function stripToolLeak(text) {
        if (!text) {
            return "";
        }
        return text
            .replace(/<(web_search|web_fetch|web_crawl|tool_call)\b[^>]*>[\s\S]*?<\/\1>/gi, "")
            .replace(/<(web_search|web_fetch|web_crawl|tool_call)\b[\s\S]*/gi, "")
            .trim();
    }

    function renderDebug(assistant, payload) {
        let box = assistant.querySelector(".debug-card");
        if (!box) {
            box = document.createElement("pre");
            box.className = "debug-card";
            assistant.insertBefore(box, assistant.querySelector(".md") || assistant.lastChild);
        }
        const lines = [
            "Intent: " + (payload.capability || payload.intent || ""),
            "Knowledge: " + (payload.knowledge || ""),
            "Follow-up: " + (payload.followUp ? "true" : "false"),
            "Route: " + ((payload.routing && payload.routing[0] && payload.routing[0].domain) || payload.domain || ""),
            "Tool: " + (payload.allowedTool || "none")
        ];
        if (payload.reference) {
            lines.push("Reference: " + payload.reference);
        }
        box.textContent = lines.join("\n");
    }

    async function loadTools() {
        const response = await fetch(API_BASE + "/tools");
        if (!response.ok) {
            return;
        }
        const tools = await response.json();
        toolList.replaceChildren();
        tools.forEach((tool) => {
            const item = document.createElement("li");
            const row = document.createElement("div");
            row.className = "tool-row " + tool.state;
            const mark = tool.state === "on" ? "✓" : tool.state === "mock" ? "◐" : "○";
            row.innerHTML = "<span class=\"mark\"></span><span></span>";
            row.querySelector(".mark").textContent = mark;
            row.querySelector("span:last-child").textContent = tool.name + " — " + tool.detail;
            item.appendChild(row);
            toolList.appendChild(item);
        });
    }

    function setSidebarHidden(hidden) {
        sidebar.classList.toggle("hidden", hidden);
        document.body.classList.toggle("sidebar-hidden", hidden);
        localStorage.setItem(SIDEBAR_KEY, hidden ? "1" : "0");
    }

    function paintMarkdown(target, raw, quiet) {
        target.classList.remove("draft");
        target.dataset.raw = raw || "";
        target.replaceChildren();
        const fragment = renderMarkdown(raw || "");
        target.appendChild(fragment);
        if (!quiet) {
            maybeStick();
        }
    }

    function renderMarkdown(src) {
        const fragment = document.createDocumentFragment();
        const lines = (src || "").replace(/\r\n/g, "\n").split("\n");
        let i = 0;
        while (i < lines.length) {
            const line = lines[i];
            if (!line.trim()) {
                i += 1;
                continue;
            }
            if (line.trim().startsWith("```")) {
                const lang = line.trim().slice(3).trim();
                i += 1;
                const code = [];
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    code.push(lines[i]);
                    i += 1;
                }
                if (i < lines.length) {
                    i += 1;
                }
                fragment.appendChild(codeBox(code.join("\n"), lang));
                continue;
            }
            if (isTableRow(line) && i + 1 < lines.length && isTableDivider(lines[i + 1])) {
                const rows = [];
                while (i < lines.length && isTableRow(lines[i])) {
                    if (!isTableDivider(lines[i])) {
                        rows.push(splitTableRow(lines[i]));
                    }
                    i += 1;
                }
                fragment.appendChild(renderTable(rows));
                continue;
            }
            if (/^#{1,3}\s+/.test(line)) {
                const level = line.match(/^#+/)[0].length;
                const heading = document.createElement(level === 1 ? "h3" : "h4");
                heading.className = "md-h";
                appendInline(heading, line.replace(/^#{1,3}\s+/, ""));
                fragment.appendChild(heading);
                i += 1;
                continue;
            }
            if (line.trim() === "---" || line.trim() === "***") {
                fragment.appendChild(document.createElement("hr"));
                i += 1;
                continue;
            }
            if (line.trim().startsWith(">")) {
                const quote = [];
                while (i < lines.length && lines[i].trim().startsWith(">")) {
                    quote.push(lines[i].replace(/^\s*>\s?/, ""));
                    i += 1;
                }
                fragment.appendChild(quoteBox(quote.join("\n")));
                continue;
            }
            if (/^\s*[-*]\s+/.test(line)) {
                const list = document.createElement("ul");
                list.className = "md-list";
                while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
                    const item = document.createElement("li");
                    appendInline(item, lines[i].replace(/^\s*[-*]\s+/, ""));
                    list.appendChild(item);
                    i += 1;
                }
                fragment.appendChild(list);
                continue;
            }
            if (/^\s*\d+\.\s+/.test(line)) {
                const list = document.createElement("ol");
                list.className = "md-list";
                while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
                    const item = document.createElement("li");
                    appendInline(item, lines[i].replace(/^\s*\d+\.\s+/, ""));
                    list.appendChild(item);
                    i += 1;
                }
                fragment.appendChild(list);
                continue;
            }
            const para = [];
            while (i < lines.length && lines[i].trim()
                    && !lines[i].trim().startsWith("```")
                    && !isTableRow(lines[i])
                    && !/^#{1,3}\s+/.test(lines[i])
                    && !lines[i].trim().startsWith(">")
                    && !/^\s*[-*]\s+/.test(lines[i])
                    && !/^\s*\d+\.\s+/.test(lines[i])) {
                para.push(lines[i]);
                i += 1;
            }
            const p = document.createElement("p");
            appendInline(p, para.join(" "));
            fragment.appendChild(p);
        }
        if (!fragment.childNodes.length) {
            const p = document.createElement("p");
            p.textContent = src || "";
            fragment.appendChild(p);
        }
        return fragment;
    }

    function isTableRow(line) {
        return line.includes("|") && line.trim().length > 1;
    }

    function isTableDivider(line) {
        return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line);
    }

    function splitTableRow(line) {
        const trimmed = line.trim().replace(/^\|/, "").replace(/\|$/, "");
        return trimmed.split("|").map((cell) => cell.trim());
    }

    function renderTable(rows) {
        if (rows.length < 2) {
            return plainTable(rows);
        }
        const header = rows[0];
        const body = rows.slice(1);
        if (header.length >= 3) {
            return compareCards(header, body);
        }
        return plainTable(rows);
    }

    function compareCards(header, body) {
        const wrap = document.createElement("div");
        wrap.className = "compare";
        const labels = header.slice(1);
        labels.forEach((title, index) => {
            const card = document.createElement("article");
            card.className = "compare-card";
            const head = document.createElement("header");
            head.textContent = title;
            const list = document.createElement("dl");
            body.forEach((row) => {
                const dt = document.createElement("dt");
                dt.textContent = row[0] || "";
                const dd = document.createElement("dd");
                appendInline(dd, row[index + 1] || "");
                list.append(dt, dd);
            });
            card.append(head, list);
            wrap.appendChild(card);
        });
        return wrap;
    }

    function plainTable(rows) {
        const wrap = document.createElement("div");
        wrap.className = "table-box";
        const table = document.createElement("table");
        table.className = "md-table";
        rows.forEach((row, rowIndex) => {
            const tr = document.createElement("tr");
            row.forEach((cell) => {
                const el = document.createElement(rowIndex === 0 ? "th" : "td");
                appendInline(el, cell);
                tr.appendChild(el);
            });
            table.appendChild(tr);
        });
        wrap.appendChild(table);
        return wrap;
    }

    function codeBox(code, lang) {
        const box = document.createElement("div");
        box.className = "code-box";
        const bar = document.createElement("div");
        bar.className = "code-bar";
        const tag = document.createElement("span");
        tag.className = "code-lang";
        tag.textContent = lang || "copy";
        const btn = copyButton(code, "Copy code");
        bar.append(tag, btn);
        const pre = document.createElement("pre");
        const el = document.createElement("code");
        el.textContent = code;
        pre.appendChild(el);
        box.append(bar, pre);
        return box;
    }

    function quoteBox(text) {
        const box = document.createElement("div");
        box.className = "copy-box";
        const bar = document.createElement("div");
        bar.className = "code-bar";
        const tag = document.createElement("span");
        tag.className = "code-lang";
        tag.textContent = "Copy this";
        bar.append(tag, copyButton(text, "Copy"));
        const body = document.createElement("p");
        body.className = "copy-box-text";
        appendInline(body, text.replace(/\n/g, " "));
        box.append(bar, body);
        return box;
    }

    function copyButton(text, label) {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "ghost copy-bit";
        btn.textContent = label;
        btn.addEventListener("click", () => copyText(text, btn));
        return btn;
    }

    async function copyText(text, btn) {
        const previous = btn.textContent;
        try {
            await navigator.clipboard.writeText(text || "");
            btn.textContent = "Copied";
        } catch {
            btn.textContent = "Copy failed";
        }
        setTimeout(() => {
            btn.textContent = previous;
        }, 1200);
    }

    function appendInline(parent, text) {
        const source = text || "";
        const parts = source.split(/(`[^`]+`|\*\*[^*]+\*\*|\*[^*]+\*)/g);
        parts.forEach((part) => {
            if (!part) {
                return;
            }
            if (part.startsWith("`") && part.endsWith("`") && part.length >= 2) {
                const code = document.createElement("code");
                code.className = "inline-code";
                code.textContent = part.slice(1, -1);
                parent.appendChild(code);
                return;
            }
            if (part.startsWith("**") && part.endsWith("**") && part.length >= 4) {
                const strong = document.createElement("strong");
                strong.textContent = part.slice(2, -2);
                parent.appendChild(strong);
                return;
            }
            if (part.startsWith("*") && part.endsWith("*") && part.length >= 2) {
                const em = document.createElement("em");
                em.textContent = part.slice(1, -1);
                parent.appendChild(em);
                return;
            }
            parent.appendChild(document.createTextNode(part));
        });
    }

    function wireComposer() {
        prompt.addEventListener("input", resizePrompt);
        prompt.addEventListener("paste", () => setTimeout(resizePrompt, 0));
        window.addEventListener("resize", () => {
            resizePrompt();
            syncScrollUi();
        });
        expandComposer.addEventListener("click", () => {
            setComposerExpanded(!composerShell.classList.contains("expanded"));
            resizePrompt();
            prompt.focus();
        });
    }

    function wireThreadScroll() {
        thread.addEventListener("scroll", () => {
            stickToBottom = isNearBottom();
            syncScrollUi();
        }, { passive: true });
        thread.addEventListener("click", (event) => {
            if (event.target === thread) {
                prompt.focus();
            }
        });
        scrollTopBtn.addEventListener("click", () => {
            stickToBottom = false;
            scrollThreadTo("top", true);
            syncScrollUi();
        });
        scrollBottomBtn.addEventListener("click", jumpToLatest);
        jumpLatest.addEventListener("click", jumpToLatest);
        document.addEventListener("keydown", (event) => {
            if (event.altKey && event.key === "ArrowUp") {
                event.preventDefault();
                stickToBottom = false;
                scrollThreadTo("top", true);
            } else if (event.altKey && event.key === "ArrowDown") {
                event.preventDefault();
                jumpToLatest();
            }
        });
    }

    function jumpToLatest() {
        stickToBottom = true;
        scrollThreadTo("bottom", true);
        syncScrollUi();
    }

    function resizePrompt() {
        const expanded = composerShell.classList.contains("expanded");
        prompt.style.height = "auto";
        const min = expanded ? Math.round(window.innerHeight * 0.22) : 48;
        const max = Math.round(window.innerHeight * (expanded ? 0.55 : 0.4));
        const needed = Math.max(prompt.scrollHeight, min);
        prompt.style.height = Math.min(needed, max) + "px";
        prompt.style.overflowY = needed > max ? "auto" : "hidden";
    }

    function setComposerExpanded(expanded) {
        composerShell.classList.toggle("expanded", expanded);
        expandComposer.textContent = expanded ? "Collapse" : "Expand";
        expandComposer.setAttribute("aria-label", expanded ? "Collapse message box" : "Expand message box");
        expandComposer.title = expanded ? "Collapse" : "Expand";
    }

    function showBusyStatus(status, label, kind) {
        const nextKind = kind || "thinking";
        if (status.dataset.mode === "busy" && status.dataset.kind === nextKind && status.dataset.label === label) {
            return;
        }
        status.dataset.mode = "busy";
        status.dataset.kind = nextKind;
        status.dataset.label = label;
        status.className = "status is-busy is-" + nextKind;
        let text = status.querySelector(".status-label");
        let dots = status.querySelector(".status-dots");
        if (!text || !dots) {
            status.replaceChildren();
            text = document.createElement("span");
            text.className = "status-label";
            dots = document.createElement("span");
            dots.className = "status-dots";
            dots.setAttribute("aria-hidden", "true");
            for (let i = 0; i < 8; i += 1) {
                dots.appendChild(document.createElement("span"));
            }
            status.append(text, dots);
        }
        text.textContent = label;
    }

    function clearStatus(status) {
        status.dataset.mode = "";
        status.dataset.kind = "";
        status.dataset.label = "";
        status.className = "status";
        status.replaceChildren();
    }

    function syncEmptyState() {
        chatStage.classList.toggle("has-messages", thread.children.length > 0);
    }

    function isNearBottom() {
        return thread.scrollHeight - thread.scrollTop - thread.clientHeight < 72;
    }

    function isNearTop() {
        return thread.scrollTop < 48;
    }

    function scrollThreadTo(where, smooth) {
        thread.scrollTo({
            top: where === "top" ? 0 : thread.scrollHeight,
            behavior: smooth ? "smooth" : "auto"
        });
    }

    function maybeStick() {
        if (stickToBottom) {
            scrollThreadTo("bottom", false);
        }
        syncScrollUi();
    }

    function syncScrollUi() {
        const canScroll = thread.scrollHeight > thread.clientHeight + 8;
        const atTop = isNearTop();
        const atBottom = isNearBottom();
        scrollTopBtn.hidden = !canScroll || atTop;
        scrollBottomBtn.hidden = !canScroll || atBottom;
        jumpLatest.hidden = !canScroll || atBottom || !sending;
    }
})();
