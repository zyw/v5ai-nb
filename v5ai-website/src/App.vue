<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';

const githubUrl = 'https://github.com/zyw/v5ai-nb';
const menuOpen = ref(false);
const currentYear = new Date().getFullYear();

const closeMenu = () => { menuOpen.value = false; };
const handleKeydown = (event: KeyboardEvent) => {
  if (event.key === 'Escape') closeMenu();
};

onMounted(() => document.addEventListener('keydown', handleKeydown));
onUnmounted(() => document.removeEventListener('keydown', handleKeydown));
</script>

<template>
  <a class="skip-link" href="#main-content">跳到主要内容</a>
  <div class="page-shell" :class="{ 'menu-open': menuOpen }">
    <header class="site-header">
      <a class="brand" href="#top" aria-label="v5ai-nb 首页"><span class="brand-mark" aria-hidden="true"><span /><span /><span /></span><span>v5ai<span class="brand-suffix">-nb</span></span></a>
      <button class="menu-toggle" type="button" :aria-expanded="menuOpen" aria-controls="site-nav" :aria-label="menuOpen ? '关闭导航菜单' : '打开导航菜单'" @click="menuOpen = !menuOpen"><span /><span /></button>
      <nav id="site-nav" class="site-nav" :class="{ 'is-open': menuOpen }" aria-label="主导航">
        <a href="#capabilities" @click="closeMenu">能力</a>
        <a href="#runtime" @click="closeMenu">运行时</a>
        <a href="#principles" @click="closeMenu">为什么是 v5ai-nb</a>
        <a class="nav-github" :href="githubUrl" target="_blank" rel="noreferrer" aria-label="在 GitHub 上查看 v5ai-nb"><span>GitHub</span><span aria-hidden="true">↗</span></a>
      </nav>
    </header>

    <main id="main-content">
      <section id="top" class="hero section-wrap">
        <div class="hero-copy" data-reveal><p class="eyebrow"><span class="eyebrow-dot" />Open source · production-minded</p><h1>让 Agent<br /><em>保持在掌控之中。</em></h1><p class="hero-lede">v5ai-nb 是一套面向生产的开源 AI 应用平台。把模型、知识、工具与运行记录放进一条清晰的运行链路里。</p><div class="hero-actions"><a class="button button-primary" href="#capabilities">探索平台 <span aria-hidden="true">↓</span></a><a class="button button-quiet" :href="githubUrl" target="_blank" rel="noreferrer">查看 GitHub <span aria-hidden="true">↗</span></a></div><div class="hero-meta" aria-label="平台特点"><span><i aria-hidden="true" />JDK 21</span><span><i aria-hidden="true" />Spring Boot 4</span><span><i aria-hidden="true" />AgentScope 2</span></div></div>
        <div class="runtime-window" aria-label="Agent 运行时示意" data-reveal><div class="window-topbar"><div class="window-lights" aria-hidden="true"><b /><b /><b /></div><span class="window-title">agent://research-assistant/run_0248</span><span class="window-live"><i />LIVE</span></div><div class="window-body"><div class="run-summary"><div><span class="run-label">AGENT RUN</span><strong>Knowledge guide</strong></div><span class="run-status"><i /> streaming</span></div><div class="event-list"><div class="event event-complete"><span class="event-icon">✓</span><div><b>MODEL_RESOLVED</b><small>qwen-plus · secondary: gpt-4.1-mini</small></div><time>12ms</time></div><div class="event event-complete"><span class="event-icon">⌁</span><div><b>RETRIEVAL</b><small>3 chunks · knowledge://product-docs</small></div><time>184ms</time></div><div class="event event-active"><span class="event-icon">↗</span><div><b>TOOL_CALL</b><small>mcp://internal/search · awaiting result</small></div><span class="event-pulse" /></div><div class="event event-pending"><span class="event-icon">○</span><div><b>TEXT_DELTA</b><small>streaming response to session_9a2f</small></div></div></div><div class="window-footer"><span>run_id <b>run_0248</b></span><span>tokens <b>1,284</b></span><span class="footer-accent">● persisted</span></div></div></div>
      </section>

      <section class="signal-strip section-wrap" aria-label="平台价值"><span>One platform</span><i /><span>Every model</span><i /><span>Clearer runs</span></section>

      <section id="capabilities" class="capabilities section-wrap"><div class="section-heading" data-reveal><p class="eyebrow">The control plane</p><h2>从模型接入，到<br /><span>可复盘的每一次运行。</span></h2><p>把分散的 AI 能力组织成可配置、可审计、可持续演进的产品基础设施。</p></div><div class="capability-grid"><article class="capability capability-wide" data-reveal><div class="capability-top"><span class="capability-number">01</span><span class="capability-icon" aria-hidden="true">◒</span></div><h3>模型，不再是黑盒</h3><p>统一管理对话、Embedding 与多供应商凭据。发布快照让线上运行和编辑态边界清楚，切换模型也有迹可循。</p><div class="model-pills"><span>OpenAI-compatible</span><span>DashScope</span><span>PgVector</span></div></article><article class="capability" data-reveal><div class="capability-top"><span class="capability-number">02</span><span class="capability-icon" aria-hidden="true">⌁</span></div><h3>知识，随时可用</h3><p>文档解析、向量检索、引用回传，一条完整 RAG 链路。</p><div class="mini-line"><span /></div></article><article class="capability" data-reveal><div class="capability-top"><span class="capability-number">03</span><span class="capability-icon" aria-hidden="true">⟐</span></div><h3>工具，接入现场</h3><p>通过 MCP 连接外部世界，权限、发现、审计一个闭环。</p><div class="tool-tags"><span>HTTP</span><span>SSE</span><span>stdio</span></div></article><article class="capability capability-dark" data-reveal><div class="capability-top"><span class="capability-number">04</span><span class="capability-icon" aria-hidden="true">✦</span></div><h3>Skill，让能力可复用</h3><p>把团队经验装进可发布、可回滚的 Skill 包，在 Agent 运行时按需注入。</p><div class="skill-code"><span>skills/</span><b>research-guide</b><span>/SKILL.md</span></div></article><article class="capability capability-accent" data-reveal><div class="capability-top"><span class="capability-number">05</span><span class="capability-icon" aria-hidden="true">∿</span></div><h3>Workflow，正在展开</h3><p>条件、模板、节点运行与状态机，为更复杂的业务编排做好准备。</p><a :href="githubUrl" target="_blank" rel="noreferrer" class="inline-link">跟进项目进度 <span aria-hidden="true">↗</span></a></article></div></section>

      <section id="runtime" class="runtime-section section-wrap"><div class="section-heading section-heading-split" data-reveal><div><p class="eyebrow">Designed for the run</p><h2>每一步都能<br /><span>被看见、被理解。</span></h2></div><p>从第一次请求到最后一个 token，v5ai-nb 把运行事件、消息、用量和审计记录在同一条线上。问题发生时，你不必猜。</p></div><div class="flow-diagram" data-reveal aria-label="Agent 运行流程"><div class="flow-node"><span class="flow-symbol">01</span><b>Resolve</b><small>发布快照</small></div><span class="flow-connector" /><div class="flow-node flow-node-hot"><span class="flow-symbol">02</span><b>Augment</b><small>RAG · MCP · Skill</small></div><span class="flow-connector" /><div class="flow-node"><span class="flow-symbol">03</span><b>Stream</b><small>事件流输出</small></div><span class="flow-connector" /><div class="flow-node"><span class="flow-symbol">04</span><b>Persist</b><small>运行与用量</small></div></div><div class="runtime-notes"><div data-reveal><span class="note-mark">↳</span><div><b>发布态与编辑态分离</b><p>线上使用稳定快照，调试使用实时配置。改动何时生效，系统说得清楚。</p></div></div><div data-reveal><span class="note-mark">◎</span><div><b>平台级治理内建</b><p>API Key、配额、限流、审计和用量，不是上线之后再补的插件。</p></div></div><div data-reveal><span class="note-mark">≋</span><div><b>流式体验原生支持</b><p>文本、工具、检索、思考与完成状态，都以可消费的事件向前端流动。</p></div></div></div></section>

      <section id="principles" class="principles section-wrap"><div class="principles-card" data-reveal><div class="principles-intro"><p class="eyebrow">Built to be yours</p><h2>你的平台，<br /><em>你的边界。</em></h2></div><div class="principles-list"><div><span>01</span><p><b>自托管</b><br />数据、凭据与运行记录留在你的环境。</p></div><div><span>02</span><p><b>可扩展</b><br />模块化单体，能力沿清晰端口演进。</p></div><div><span>03</span><p><b>可观测</b><br />每一次 Agent 运行都能被解释和复盘。</p></div></div></div></section>
      <section class="closing section-wrap" data-reveal><p class="eyebrow">Start from the source</p><h2>把下一次 Agent 运行，<br /><span>交给更清晰的基础设施。</span></h2><a class="button button-primary button-large" :href="githubUrl" target="_blank" rel="noreferrer">在 GitHub 上开始 <span aria-hidden="true">↗</span></a></section>
    </main>

    <footer class="site-footer section-wrap"><div class="footer-brand"><span class="brand-mark" aria-hidden="true"><span /><span /><span /></span><span>v5ai-nb</span></div><p>Open source AI application platform for teams that build with intent.</p><div class="footer-meta"><span>© {{ currentYear }} v5ai-nb</span><a :href="githubUrl" target="_blank" rel="noreferrer">GitHub ↗</a></div></footer>
  </div>
</template>
