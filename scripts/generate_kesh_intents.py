#!/usr/bin/env python3
"""Write Kesh capability train/eval JSONL. Human-authored templates, not model labels."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TRAIN = []
EVAL = []


def add(split: str, text: str, intent: str, domain: str | None = None, extra: list[str] | None = None, **entities):
    row = {"text": text, "intent": intent, "domain": domain or intent.split("_")[0] if False else None}
    # domain from map
    DOMAIN = {
        "CHAT_GENERAL": "CHAT",
        "LLM_EXPLAIN": "LLM", "LLM_COMPARE": "LLM", "LLM_DESIGN": "LLM", "LLM_CODE_HELP": "LLM",
        "LLM_SUMMARIZE": "LLM", "LLM_FORMAT": "LLM",
        "RUNTIME_INSPECT": "SYSTEM", "APP_OPEN": "SYSTEM", "APP_FOCUS": "SYSTEM", "APP_QUIT": "SYSTEM", "SYSTEM_INFO": "SYSTEM",
        "FILE_READ": "TOOLS", "FILE_SEARCH": "TOOLS", "FILE_LIST": "TOOLS", "FILE_METADATA": "TOOLS", "GIT": "TOOLS",
        "WEB_SEARCH": "WEB", "WEB_FETCH": "WEB", "WEB_RESEARCH": "WEB", "WEB_CRAWL": "WEB",
        "RAG_QUERY": "RAG", "RAG_SUMMARIZE": "RAG", "RAG_COMPARE": "RAG", "RAG_DOCUMENT_RESEARCH": "RAG",
        "SLACK": "WORK", "JIRA": "WORK", "CONFLUENCE": "WORK", "GITHUB": "WORK", "GMAIL": "WORK", "CALENDAR": "WORK",
        "TERMINAL_EXECUTION": "TERMINAL", "JAVA_SNIPPET_EXECUTION": "JAVA", "DESTRUCTIVE": "TOOLS",
    }
    row["domain"] = DOMAIN[intent]
    if entities:
        row["entities"] = entities
    if extra:
        row["intents"] = [intent, *extra]
    (EVAL if split == "eval" else TRAIN).append(row)


def many(split, intent, texts, **kw):
    for t in texts:
        add(split, t, intent, **kw)


many("train", "CHAT_GENERAL", [
    "hello", "hi", "Hi", "hi there", "hey", "good morning", "good evening", "how are you",
    "what can you do", "who are you", "thanks that helps", "thank you",
    "ok thanks", "never mind", "got it", "cool", "yo", "hi kesh",
    "can you help me", "are you there", "good afternoon",
])
many("eval", "CHAT_GENERAL", ["hey kesh", "thanks a lot", "morning"])

many("train", "LLM_EXPLAIN", [
    "explain CAP theorem", "what is Kafka", "explain REST", "what is dependency injection",
    "what is Spring Boot", "explain how kafka works", "tell me what a mutex is",
    "what does eventual consistency mean", "explain this error message",
    "what is the CAP theorem in distributed systems", "describe HTTP status codes",
    "what is a JVM", "explain backpressure", "what is CQRS",
    "1+1", "2+2", "what is 2+2",
])
many("eval", "LLM_EXPLAIN", ["Explain REST", "What is Kafka?", "what does this stack trace mean", "1+1"])

many("train", "LLM_COMPARE", [
    "compare Java and Clojure", "what is the difference between rust and go",
    "Okay, let’s compare Clojure and Java. Use a markdown table.",
    "compare Redis and Memcached", "Java vs Kotlin for backends",
    "difference between REST and GraphQL", "compare postgres and mysql",
    "Clojure versus Java for concurrency", "contrast TCP and UDP",
])
many("eval", "LLM_COMPARE", ["compare Python and Java", "what's the difference between lists and vectors"])

many("train", "LLM_DESIGN", [
    "design a notification system", "how would you design a rate limiter",
    "sketch an architecture for chat", "help me think through this design",
    "can you help me think through this design", "propose a folder structure for a Spring service",
])
many("eval", "LLM_DESIGN", ["design an idempotent payment API"])

many("train", "LLM_CODE_HELP", [
    "Tell me how to run mvn test", "How do I run mvn test", "how can I run git status",
    "How do I open Chrome programmatically?", "How can I open Chrome?",
    "How do I search Jira?", "how do I execute docker ps",
    "show me the command I would use for git status but do not run it",
    "what is the maven command to run tests", "how to write a junit test",
    "help me debug this null pointer", "fix this java snippet",
    "how do I start a Spring Boot app from the command line, as an explanation",
    "what's the syntax for git rebase", "explain how to run lein test",
    "Hello world Java program", "hello world java program",
    "write a hello world program in Java", "java hello world",
    "print hello world in Java", "write Hello World in Java",
    "show me a hello world Java program", "simple Java hello world example",
    "create a hello world class in Java",
    "Explain this Java algorithm", "explain this Java method without running it",
    "clojure hello world", "Hello world Clojure program", "write hello world in Clojure",
])
many("eval", "LLM_CODE_HELP", [
    "Tell me how to run mvn test",
    "How do I open Chrome programmatically?",
    "How do I run git status?",
    "Hello world Java program",
])

many("train", "LLM_SUMMARIZE", [
    "summarize this idea for me", "summarize the previous answer",
    "make a short summary of CAP theorem", "tl;dr that explanation",
    "Summarize this document.",
])
many("eval", "LLM_SUMMARIZE", ["give me a concise summary of REST"])

many("train", "LLM_FORMAT", [
    "use a markdown table", "format this as bullet points",
    "rewrite that as a slack message", "turn this into a checklist",
])
many("eval", "LLM_FORMAT", ["format that comparison as a table"])

many("train", "RUNTIME_INSPECT", [
    "Which Java version is installed?", "Tell me my Java version.",
    "What version of Java am I running?", "Check Java on this machine.",
    "Can you inspect my installed Java?", "What Java runtime do I have?",
    "Which JDK is currently installed?", "Check my local Java environment.",
    "tell me what version of java installed on this mac",
    "what node version is installed", "what version of node is on this mac",
    "which cursor version", "what version of cursor is installed",
    "which is mac version", "what macos version do I have",
    "is python installed", "java -version", "node -v",
    "what version of git is on this mac", "check installed docker version on this machine",
])
many("eval", "RUNTIME_INSPECT", [
    "which Java version is installed?",
    "what version of cursor is on this mac",
    "check my installed node runtime",
])

many("train", "APP_OPEN", [
    "Open Chrome", "open Google Chrome", "open chrome", "launch Calendar",
    "open the browser", "start Notes", "please open Safari",
    "open Slack the application", "launch Cursor",
    "open BBEdit", "open bbedit",
])
many("eval", "APP_OPEN", ["Open Chrome", "launch Google Chrome"])

many("train", "APP_FOCUS", [
    "focus Safari", "bring Notes to the front", "focus Google Chrome",
    "focus Cursor", "switch to Slack",
])
many("eval", "APP_FOCUS", ["focus Chrome"])

many("train", "APP_QUIT", [
    "quit Slack", "quit Google Chrome", "close Notes app", "quit Safari",
    "close chrome", "close Google Chrome", "quit chrome",
])
many("eval", "APP_QUIT", ["quit Chrome"])

many("train", "SYSTEM_INFO", [
    "what apps are running", "list running applications", "list my running apps",
    "what is the current time", "give me current time", "what time is it",
])
many("eval", "SYSTEM_INFO", ["list running applications"])

many("train", "FILE_READ", [
    "read my README", "read the README.md file", "read this path ~/Kesh/pom.xml",
    "read src/main/resources/application.yml", "open the notes in Documents/Kesh",
    "look at the markdown notes", "read my README and summarize it",
    "show me the contents of pom.xml",
])
many("eval", "FILE_READ", ["read the README.md file in ~/Kesh"])

many("train", "FILE_SEARCH", [
    "search java files for KafkaConsumer", "search files containing TODO",
    "find authentication code in my project", "search my project for authentication",
    "find all yaml files", "search my documents for release notes",
])
many("eval", "FILE_SEARCH", ["search my project for authentication"])

many("train", "FILE_LIST", [
    "list files in ~/Kesh", "list the directory ~/Documents/Kesh",
    "what files are in this repo", "show me what is in the Projects folder",
    "list files in my project",
])
many("eval", "FILE_LIST", ["list files in my project"])

many("train", "WEB_SEARCH", [
    "what is the latest Java version?", "What's the latest Spring Boot release?",
    "what's the latest Spring Boot version", "What is the latest Java version",
    "current Java release", "latest Spring Boot release",
    "search the web for recent Kafka developments", "search the web for Spring Boot 4",
    "look up Redis documentation", "find recent information about Kafka",
    "can you check weather in Pune", "what's the weather in London",
    "how is weather in Pune", "how is the weather in Pune", "how's the weather in Pune",
    "how is weather in Mumbai", "weather in Pune", "Pune weather today",
    "what is the weather like in Pune", "how's weather in Pune today",
    "weather forecast for Mumbai today", "search the internet for authentication best practices",
    "What does Kafka documentation say?", "what changed in Spring Boot recently?",
    "find the latest Kafka documentation",
    "rain chances?", "will it rain in Pune", "Pune rain forecast", "chance of rain tomorrow",
    "humidity in Pune", "will it rain tomorrow in Pune",
])
many("eval", "WEB_SEARCH", [
    "what is the latest Java version?",
    "latest Java version",
    "What's the latest Spring Boot release?",
    "can you check weather in Pune",
    "how is weather in Pune",
    "What does Kafka documentation say?",
])

many("train", "WEB_FETCH", [
    "Read https://spring.io and explain it", "Read https://spring.io and summarize it",
    "fetch https://example.com", "Read this page and summarize it https://kafka.apache.org",
    "open https://docs.oracle.com/en/java/ and summarize",
])
many("eval", "WEB_FETCH", ["Read https://spring.io and explain it"])

many("train", "WEB_RESEARCH", [
    "Research CertifyOS and tell me about its technology stack.",
    "Research Spring Boot and Kafka integration",
    "research the current state of Kafka exactly-once processing",
    "Research this topic across multiple pages: Redis persistence",
    "do a web research on Java 25 features",
])
many("eval", "WEB_RESEARCH", ["Research the current state of Kafka exactly-once processing."])

many("train", "WEB_CRAWL", [
    "crawl https://spring.io/projects/spring-boot and collect related pages",
    "crawl this docs site starting at https://kafka.apache.org/documentation/",
])
many("eval", "WEB_CRAWL", ["crawl https://example.com/docs"])

many("train", "RAG_QUERY", [
    "what did we decide about releases?", "find information about our onboarding process",
    "what does our documentation say about Kafka?", "What does our README say about authentication?",
    "what documents do I have about onboarding?", "Find documents about this topic.",
    "what was decided about the release process?",
])
many("eval", "RAG_QUERY", [
    "what does our documentation say about Kafka?",
    "What does our README say about authentication?",
])

many("train", "RAG_SUMMARIZE", [
    "summarize our architecture documents", "summarize all documents related to our release process",
    "Read my architecture documents and create a summary.",
    "summarize all documents about release process",
])
many("eval", "RAG_SUMMARIZE", ["summarize our architecture documents"])

many("train", "RAG_COMPARE", [
    "compare our architecture notes on caching",
    "compare what our docs say about Kafka vs our last chat",
])
many("eval", "RAG_COMPARE", ["compare our onboarding notes"])

many("train", "RAG_DOCUMENT_RESEARCH", [
    "Read all my architecture documents, find the sections related to caching",
    "gather every local note about caching",
])
many("eval", "RAG_DOCUMENT_RESEARCH", ["research our local docs about caching"])

many("train", "SLACK", [
    "show my unread slack messages", "any slack mentions for me",
    "show unread Slack messages", "what are my unread Slack messages?",
])
many("eval", "SLACK", ["show my unread slack messages"])

many("train", "JIRA", [
    "list my jira issues", "what is on my jira board", "what are my Jira issues?",
    "Search Jira", "Search Jira for my tickets.", "show jira tickets assigned to me",
    "Check our Jira tickets and tell me which ones need attention.",
])
many("eval", "JIRA", ["Search Jira", "what are my Jira issues?"])

many("train", "CONFLUENCE", [
    "search confluence for onboarding", "look up the confluence page",
    "find the latest Confluence page about caching",
])
many("eval", "CONFLUENCE", ["search confluence for onboarding"])

many("train", "GITHUB", [
    "show my github pull requests", "any new PRs on github",
    "list my open GitHub PRs",
])
many("eval", "GITHUB", ["show my github pull requests"])

many("train", "GMAIL", [
    "check unread gmail", "inbox in gmail", "show unread emails",
])
many("eval", "GMAIL", ["check unread gmail"])

many("train", "CALENDAR", [
    "what's on my calendar today", "show my calendar events",
])
many("eval", "CALENDAR", ["what's on my calendar"])

many("train", "TERMINAL_EXECUTION", [
    "run mvn test", "Run mvn test in my project", "execute git status", "Run git status.",
    "run docker ps", "run a shell command", "execute brew list", "run gradle build",
    "open a terminal and run ls", "please run npm install", "run lein test",
    "execute find in the project", "Run curl https://example.com",
])
many("eval", "TERMINAL_EXECUTION", [
    "run mvn test",
    "Run git status.",
    "Run curl https://example.com",
])

many("train", "DESTRUCTIVE", [
    "delete this file", "remove notes.md", "wipe that folder", "rm the temp directory",
    "delete everything in Documents/Kesh", "trash these files", "overwrite application.yml",
])
many("eval", "DESTRUCTIVE", ["delete this file notes.md"])

# multi-capability
add("train", "Find the latest information about Java 25, compare it with what we currently use in my project, and summarize the differences.",
    "WEB_SEARCH", extra=["FILE_SEARCH", "LLM_COMPARE"])
add("train", "Find the latest Kafka documentation and compare it with our implementation.",
    "WEB_SEARCH", extra=["RAG_QUERY", "LLM_COMPARE"])
add("train", "Read my architecture documents and create a summary.",
    "RAG_DOCUMENT_RESEARCH", extra=["LLM_SUMMARIZE"])
add("train", "Search the web for Redis sorted sets and explain how we use them.",
    "WEB_SEARCH", extra=["RAG_QUERY", "LLM_EXPLAIN"])
add("train", "research Kafka exactly-once semantics and compare it with our implementation",
    "WEB_RESEARCH", extra=["FILE_SEARCH", "LLM_COMPARE"])
add("train", "show my unread Slack messages and summarize them",
    "SLACK", extra=["LLM_SUMMARIZE"])
add("train", "read my README and summarize it",
    "FILE_READ", extra=["LLM_SUMMARIZE"])
add("eval", "research Kafka exactly-once semantics and compare it with our implementation",
    "WEB_RESEARCH", extra=["FILE_SEARCH", "LLM_COMPARE"])
add("eval", "Find the latest Kafka documentation and compare it with our implementation.",
    "WEB_SEARCH", extra=["RAG_QUERY", "LLM_COMPARE"])

# extra variety
many("train", "WEB_SEARCH", [
    "what's the current public JDK release", "look up the newest Spring Boot milestone",
    "google the latest kafka kRaft status", "any news on Java 25",
])
many("train", "CHAT_GENERAL", ["nice", "sounds good", "that worked", "please continue"])
many("train", "LLM_EXPLAIN", ["walk me through two-phase commit", "what is a sidecar proxy"])
many("train", "FILE_READ", ["open README.md from the repo root", "display application.yml"])
many("train", "RUNTIME_INSPECT", ["which python is on this mac", "installed java version please"])
many("train", "CHAT_GENERAL", [
    "hello again", "hi friend", "all good", "appreciate it", "that is helpful",
    "stop there", "let's pause", "another question later",
])
many("train", "LLM_EXPLAIN", [
    "what is a deadlock", "explain optimistic locking", "what is a circuit breaker",
    "tell me about the actor model", "what is hex architecture",
    "what is the 100th Fibonacci number", "calculate 2 to the power of 20",
    "1+1", "2+2", "1 + 1", "2 + 2", "what is 1+1", "what's 2+2",
    "3*4", "10-3", "100/4",
])
many("train", "LLM_COMPARE", [
    "compare Kafka and RabbitMQ", "monolith vs microservices",
    "compare checked and unchecked exceptions",
])
many("train", "LLM_CODE_HELP", [
    "how do I write a Dockerfile for Spring Boot",
    "how can I sort a Java stream",
    "how do I open a file in Java, as an explanation only",
    "what's the kubectl command to list pods, don't run it",
])
many("train", "WEB_SEARCH", [
    "hats the weather in Pune", "whats the weather in Pune", "what's the weather in Pune",
    "web search", "search the web",
    "newest Java version", "latest JDK version", "current public Java version",
    "what Java version is current", "newest Spring Boot version",
    "Harley 440T price in Pune", "Harley Davidson 440T on road price Pune",
    "best car to buy", "best cars to buy in India",
    "stripe", "infosys", "zendesk", "who is Infosys", "what is Stripe",
    "what is Zendesk", "Infosys stock price", "infosys share price",
    "Stripe company", "Zendesk company",
])
many("train", "FILE_SEARCH", [
    "find KafkaConsumer in the repo", "where is authentication implemented",
    "grep my project for FeatureFlag",
])
many("train", "FILE_LIST", [
    "list src/main/java", "show the folders in ~/Projects",
])
many("train", "TERMINAL_EXECUTION", [
    "run mvn -q test", "execute ./mvnw verify", "run git diff",
])
many("train", "JIRA", [
    "open my assigned jira tickets", "any blocked jira issues",
])
many("train", "SLACK", [
    "any unread dms on slack", "just fetch my slack mentions",
])
many("train", "RAG_QUERY", [
    "what did we say about caching last time", "remind me our decision on caching",
])
many("eval", "LLM_CODE_HELP", ["How do I search Jira?", "how can I open Chrome?"])
many("eval", "WEB_SEARCH", ["search the internet for authentication best practices"])
many("eval", "APP_OPEN", ["open Safari"])
many("eval", "FILE_LIST", ["what files are in this repo"])
many("eval", "RAG_QUERY", ["what did we decide about releases?"])
many("eval", "TERMINAL_EXECUTION", ["Run mvn test in my project"])


def write(path: Path, rows: list[dict]):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as fh:
        for row in rows:
            fh.write(json.dumps(row, ensure_ascii=False) + "\n")


def main():
    write(ROOT / "data" / "kesh-intents.jsonl", TRAIN)
    write(ROOT / "data" / "evaluation.jsonl", EVAL)
    write(ROOT / "src" / "main" / "resources" / "nlp" / "kesh-intents.jsonl", TRAIN)
    write(ROOT / "src" / "main" / "resources" / "nlp" / "evaluation.jsonl", EVAL)
    print(f"train={len(TRAIN)} eval={len(EVAL)}")


if __name__ == "__main__":
    main()
