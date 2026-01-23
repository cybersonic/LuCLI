---
title: About LuCLI
subtitle: A focused Lucee command‑line toolkit
layout: page
draft: false
---

LuCLI is a small, focused command‑line interface for [Lucee](https://lucee.org/) built by **Mark Drew**.

It’s written in **Java**, built with **Maven**, and runs Lucee under the hood, using **picocli** for the CLI plumbing and **JLine** for the interactive terminal experience. LuCLI packages all of that into a single tool you can use to run CFML, manage Lucee servers, and script everyday workflows from your shell.

## Why I built LuCLI

I’ve been working with Lucee for many years, both on real‑world projects and closely with the Lucee team itself. Over time I found myself reaching for the same patterns again and again:

- Starting and stopping ad‑hoc Lucee servers for different projects
- Inspecting configuration and experimenting with CFML snippets
- Automating repeatable tasks and one‑off maintenance jobs

There are great general‑purpose tools out there, but I wanted a **Lucee‑specific toolkit** that understood CFML, Lucee configuration, and common Lucee workflows out of the box.

LuCLI is that toolkit: opinionated, pragmatic, and built hand‑in‑hand with experience from real Lucee deployments.

## How this site is built

This website is generated using **[Markspresso](https://markspresso.org)** — a LuCLI module that brews static HTML sites from Markdown content. The docs and pages you’re reading are written as Markdown, processed by Markspresso, and published via LuCLI.

That means the tool you install to run Lucee from your terminal is also the tool that builds and ships its own documentation site.

## Philosophy

A few principles guide how I approach LuCLI:

- **Be Lucee‑first.** Embrace Lucee’s strengths and quirks instead of hiding them.
- **Stay small and focused.** Fewer features, better integrated, focus on the developer experience.
- **CLI‑native.** First‑class support for terminals, scripting, and automation.
- **Transparent and scriptable.** If you can do it once, you should be able to automate it.

## What’s next

LuCLI is an evolving project. I’m interested in:

- **Deepening integration with Lucee**: more introspection, better diagnostics, richer server tooling.
- **Expanding the module ecosystem** so teams can share and reuse CFML‑based tools.
- **Improving the docs and examples** so it’s easier to get started and to adopt LuCLI in existing projects.

If you have ideas, feedback, or run into something you think LuCLI should make easier, I’d love to hear from you via GitHub issues or discussions on the Lucee community channels.

## Stay in touch

- **LuCLI on GitHub:** <https://github.com/cybersonic/LuCLI>
- **My GitHub profile:** <https://github.com/cybersonic>
- **Lucee community:** <https://dev.lucee.org>
- **Markspresso:** <https://markspresso.org>
