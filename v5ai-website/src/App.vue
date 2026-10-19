<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue';
const github = 'https://github.com/zyw/v5ai-nb';
const enterprise = 'https://baize.v5ai.xin';
const documentation = 'https://ai-docs.v5ai.xin';
const menuOpen = ref(false);
const workspaceScreens = [
  { label: 'Workflow', title: '工作流编排', image: '/images/product/workflow-editor.png', alt: '工作流编辑器展示节点库、条件分支、Agent 节点和右侧属性面板', description: '在画布里连接节点、设置分支，并在属性面板中配置每一步。工作流能力仍在持续完善。' },
  { label: 'Agent', title: 'Agent 详情', image: '/images/product/agent-detail.png', alt: 'Agent 编辑页展示基本信息、模型配置和右侧对话预览', description: '把模型选择、能力配置与对话预览放在同一工作区，边编辑边调试。' },
  { label: 'RAG', title: '知识库问答', image: '/images/product/rag-detail.png', alt: '知识库详情页展示知识问答、检索参数和对话测试区域', description: '在知识库内调整检索参数，直接预览和测试知识问答效果。' },
  { label: 'Skill', title: 'Skill 文件编辑', image: '/images/product/skill-editor.png', alt: 'Skill 编辑器展示文件树与 SKILL.md 文档内容', description: '在文件树中管理 Skill 内容，编辑说明与引用文件，沉淀可复用的经验。' },
  { label: 'Debug', title: '调试工具', image: '/images/product/debug-tool.png', alt: '调试工具页面展示 Agent 上下文、对话调试和运行事件时间线', description: '使用当前 Agent 配置发起调试，观察回答、能力状态和运行事件。' },
] as const;
const selectedWorkspace = ref(0);
const activeWorkspace = computed(() => workspaceScreens[selectedWorkspace.value]!);
const year = new Date().getFullYear();
const closeMenu = () => { menuOpen.value = false; };
const onKeydown = (event: KeyboardEvent) => { if (event.key === 'Escape') closeMenu(); };
onMounted(() => document.addEventListener('keydown', onKeydown));
onUnmounted(() => document.removeEventListener('keydown', onKeydown));
</script>

<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <div class="page">
    <header class="header wrap">
      <a class="brand" href="#top" aria-label="v5ai-nb 首页" @click="closeMenu"><span class="brand-mark" aria-hidden="true"></span>v5ai-nb</a>
      <button class="menu-toggle" type="button" :aria-expanded="menuOpen" aria-controls="navigation" :aria-label="menuOpen ? '关闭导航菜单' : '打开导航菜单'" @click="menuOpen = !menuOpen"><span></span><span></span></button>
      <nav id="navigation" class="navigation" :class="{ open: menuOpen }" aria-label="主导航">
        <a href="#screens" @click="closeMenu">产品界面</a>
        <a href="#platform" @click="closeMenu">平台能力</a>
        <a href="#runtime" @click="closeMenu">运行方式</a>
        <a href="#open-source" @click="closeMenu">开源与部署</a>
        <a :href="documentation" target="_blank" rel="noopener noreferrer" @click="closeMenu">文档 <span aria-hidden="true">↗</span></a>
        <a :href="enterprise" target="_blank" rel="noopener noreferrer" @click="closeMenu">企业版 <span aria-hidden="true">↗</span></a>
        <a class="button button-dark" :href="github" target="_blank" rel="noopener noreferrer" @click="closeMenu">查看 GitHub <span aria-hidden="true">↗</span></a>
      </nav>
    </header>

    <main id="main">
      <section id="top" class="hero wrap">
        <div class="hero-copy">
          <p class="hero-badge"><span></span> 开源 AI 应用平台 / 为真实业务构建</p>
          <h1>让 AI 应用<br>从想法走向<br>每一次可靠运行<span class="period">。</span></h1>
          <p class="hero-lede">在一个平台里管理模型、Agent、知识与工具。配置、发布、对话和追踪形成完整闭环，让团队更快交付可用的 AI 应用。</p>
          <div class="actions"><a class="button button-dark" :href="github" target="_blank" rel="noopener noreferrer">查看开源项目 <span aria-hidden="true">↗</span></a><a class="plain-link" href="#platform">了解平台能力 <span aria-hidden="true">↓</span></a></div>
          <p class="technology">JDK 21 · Spring Boot 4 · AgentScope Java 2.0</p>
        </div>
        <div class="mockup-wrap">
          <div class="mockup" role="img" aria-label="v5ai-nb Agent 配置和运行预览界面示意">
            <aside class="mock-sidebar"><div class="mock-logo"><span class="brand-mark"></span> v5ai-nb</div><small>工作空间</small><div class="mock-side-active">◈ &nbsp; Agent</div><div>▤ &nbsp; 模型管理</div><div>⌕ &nbsp; 知识库</div><div>⌘ &nbsp; MCP Server</div><div>✳ &nbsp; Skill</div><div class="mock-side-bottom">运行观测 ↗</div></aside>
            <div class="mock-main"><div class="mock-bar">Agent / 产品知识助手 <span>V</span></div><div class="mock-heading"><div><small>AGENT OVERVIEW</small><h2>产品知识助手</h2><p>让产品资料成为随时可用的回答能力</p></div><span class="status"><i></i> 已发布</span></div><div class="mock-columns"><div class="mock-panel"><div class="mock-panel-head">能力配置 <small>发布版本 v3</small></div><div class="setting"><span class="setting-icon blue">◇</span><p>对话模型<small>统一接入与配置</small></p><b>✓</b></div><div class="setting"><span class="setting-icon orange">▤</span><p>知识库<small>检索与引用</small></p><b>✓</b></div><div class="setting"><span class="setting-icon pink">⌘</span><p>MCP 工具<small>动态注册与权限控制</small></p><b>✓</b></div><div class="setting"><span class="setting-icon yellow">✳</span><p>Skill<small>版本发布与运行时注入</small></p><b>✓</b></div></div><div class="mock-panel"><div class="mock-panel-head">运行预览 <small class="streaming">● 流式响应</small></div><div class="question">这款产品如何处理知识库检索？</div><div class="answer"><span>✳</span><p>Agent 可以连接已绑定的知识库。运行时按配置检索相关内容，并将引用随回答返回。</p></div><div class="event-tags"><span>● 模型调用</span><span>● 知识检索</span><span>● 回答完成</span></div></div></div></div>
          </div><div class="float-note"><b>↗</b><span>运行记录<small>事件、消息与用量持续留存</small></span></div><p class="mock-caption">产品能力示意 · 非真实系统截图</p>
        </div>
      </section>
      <div class="capability-strip wrap"><span>一个平台，串起 AI 应用全流程</span><div><span>模型管理</span><span>Agent 发布</span><span>RAG 知识库</span><span>MCP · Skill</span><span>运行观测</span></div></div>

      <section id="screens" class="screens wrap" aria-labelledby="screens-title">
        <div class="screens-intro">
          <div><p class="kicker">真实产品界面</p><h2 id="screens-title">从管理工作台，<br>到每一次对话。</h2></div>
          <p>一套平台，两种使用视角。管理端负责配置与运营，对话门户让已发布的 Agent 直接服务用户。点击图片可查看原尺寸。</p>
        </div>
        <div class="screen-group">
          <div class="screen-group-heading"><span>01 / 管理端</span><h3>配置、发布与观测，集中在一处。</h3></div>
          <div class="screen-grid">
            <figure class="screen-card"><a class="screen-preview" href="/images/product/admin-login.png" target="_blank" rel="noopener noreferrer" aria-label="查看管理端登录页原图"><span class="screen-chrome" aria-hidden="true"><i></i><i></i><i></i><b>v5ai-ui · 登录</b></span><img src="/images/product/admin-login.png" alt="v5ai-nb 管理端登录页，左侧填写账号，右侧介绍 Agent、知识和模型管理能力" width="2830" height="1592" loading="lazy" decoding="async"></a><figcaption><span>01 / 身份验证</span><h4>进入管理工作台</h4><p>面向管理员的统一入口，连接平台配置与运行管理。</p></figcaption></figure>
            <figure class="screen-card"><a class="screen-preview" href="/images/product/admin-dashboard.png" target="_blank" rel="noopener noreferrer" aria-label="查看管理端总览页原图"><span class="screen-chrome" aria-hidden="true"><i></i><i></i><i></i><b>v5ai-ui · 总览</b></span><img src="/images/product/admin-dashboard.png" alt="v5ai-nb 管理端总览页，展示 Agent、模型、知识库、Skill 和运行用量概况" width="2812" height="1598" loading="lazy" decoding="async"></a><figcaption><span>02 / 平台总览</span><h4>关键资源与运行情况一眼可见</h4><p>汇集 Agent、模型、知识库、Skill 数量及用量趋势。</p></figcaption></figure>
          </div>
        </div>
        <div class="screen-group">
          <div class="screen-group-heading"><span>02 / 对话门户</span><h3>把已发布的 Agent 交到用户手中。</h3></div>
          <div class="screen-grid">
            <figure class="screen-card"><a class="screen-preview" href="/images/product/chat-login.png" target="_blank" rel="noopener noreferrer" aria-label="查看对话门户登录页原图"><span class="screen-chrome" aria-hidden="true"><i></i><i></i><i></i><b>v5ai-ui-chat · 登录</b></span><img src="/images/product/chat-login.png" alt="AI 对话门户登录页，展示能力介绍与 API Key 身份验证入口" width="2828" height="1592" loading="lazy" decoding="async"></a><figcaption><span>03 / API Key 进入</span><h4>快速进入专属对话空间</h4><p>用户凭管理员签发的 API Key 进入已发布的 Agent。</p></figcaption></figure>
            <figure class="screen-card"><a class="screen-preview" href="/images/product/chat-home.png" target="_blank" rel="noopener noreferrer" aria-label="查看对话门户主页原图"><span class="screen-chrome" aria-hidden="true"><i></i><i></i><i></i><b>v5ai-ui-chat · 主页</b></span><img src="/images/product/chat-home.png" alt="AI 对话门户主页，包含会话侧栏、Agent 介绍、建议问题和消息输入框" width="2824" height="1598" loading="lazy" decoding="async"></a><figcaption><span>04 / 开始对话</span><h4>围绕场景持续交流</h4><p>提供会话管理、建议提问和流式回答的交互入口。</p></figcaption></figure>
          </div>
        </div>
      </section>

      <section id="platform" class="platform wrap"><div class="intro"><p class="kicker">构建、发布、运营</p><h2>复杂的能力，<br>清晰地交给团队使用。</h2><p>从原型调试到正式发布，v5ai-nb 把每个关键环节放在可管理的工作台中。</p></div><div class="feature-grid">
        <article class="feature feature-large"><div class="feature-art model-art"><div class="art-head">模型中心 <small>多供应商接入</small></div><div class="model-cards"><div><i>◉</i><span>对话模型<small>连接测试 · 凭据加密</small></span><em>已连接</em></div><div><i>◎</i><span>Embedding 模型<small>支持知识库索引</small></span><em>已连接</em></div></div></div><div class="feature-copy"><h3>模型有统一入口</h3><p>管理 Provider、模型配置和加密凭据；上线前完成连通性测试，让 Agent 的模型选择有据可依。</p></div></article>
        <article class="feature"><div class="feature-art knowledge-art"><div class="art-head">知识库 <small>检索命中</small></div><div class="document"><b>PDF</b><span>产品说明文档<small>解析 · 切片 · 索引</small></span><em>✓</em></div><div class="search-fragment">⌕ &nbsp; 用户的问题与相关知识片段</div><div class="result-fragment"><i></i><i></i><i></i></div></div><div class="feature-copy"><h3>知识随回答而来</h3><p>上传文档或导入网页，交给异步索引流程处理；检索结果可以进入回答并回传引用。</p></div></article>
        <article class="feature"><div class="feature-art tools-art"><div class="art-head">扩展能力 <small>按需装配</small></div><div class="tool-chain"><span>Agent</span><i></i><span>MCP</span><i></i><span>Skill</span></div><div class="tool-subline">工具发现与授权 &nbsp; · &nbsp; Skill 发布与回滚</div></div><div class="feature-copy"><h3>工具与经验一起复用</h3><p>通过 MCP 连接外部工具，用 Skill 封装团队经验。绑定后在运行时装配，权限与调用留下记录。</p></div></article>
      </div></section>

      <section id="workspace" class="workspace-section wrap" aria-labelledby="workspace-title">
        <div class="workspace-heading"><div><p class="kicker">深入工作台</p><h2 id="workspace-title">把每一项能力，<br>放到可操作的界面里。</h2></div><p>从工作流编排到 Agent 配置、知识检索、Skill 编辑和运行调试，看看团队如何在实际界面中完成工作。</p></div>
        <div class="workspace-gallery">
          <div class="workspace-nav" aria-label="功能界面选择">
            <button v-for="(screen, index) in workspaceScreens" :key="screen.label" type="button" :class="{ active: selectedWorkspace === index }" :aria-pressed="selectedWorkspace === index" @click="selectedWorkspace = index"><span>{{ String(index + 1).padStart(2, '0') }}</span><strong>{{ screen.title }}</strong><small>{{ screen.label }}</small></button>
          </div>
          <div class="workspace-display">
            <a class="workspace-image-link" :href="activeWorkspace.image" target="_blank" rel="noopener noreferrer" :aria-label="`查看${activeWorkspace.title}原图`"><span class="screen-chrome" aria-hidden="true"><i></i><i></i><i></i><b>v5ai-ui · {{ activeWorkspace.label }}</b></span><img :src="activeWorkspace.image" :alt="activeWorkspace.alt" width="2940" height="1602" loading="lazy" decoding="async"></a>
            <div class="workspace-caption"><div><span>产品实景 / {{ String(selectedWorkspace + 1).padStart(2, '0') }}</span><h3>{{ activeWorkspace.title }}</h3><p>{{ activeWorkspace.description }}</p></div><a :href="activeWorkspace.image" target="_blank" rel="noopener noreferrer">查看原图 <span aria-hidden="true">↗</span></a></div>
          </div>
        </div>
      </section>

      <section id="runtime" class="runtime"><div class="runtime-inner wrap"><div class="runtime-copy"><p class="kicker">从请求到答案</p><h2>每一次运行，<br>都有迹可循。</h2><p class="runtime-lede">运行不只有结果。发布快照、能力装配、流式事件与持久化记录串在同一条链路上。</p><div class="runtime-points"><div><span>01</span><p><b>先发布，再稳定运行</b>线上读取 Agent 发布快照；管理端可用实时配置调试。</p></div><div><span>02</span><p><b>把过程交还给开发者</b>模型调用、检索、工具调用和回答以 SSE 事件输出。</p></div><div><span>03</span><p><b>在平台层控制使用</b>API Key、角色权限、配额、限流与审计协同治理。</p></div></div></div><div class="trace"><div class="trace-head"><span>● LIVE RUN TRACE</span><span>run_0248</span></div><div class="trace-row"><i>✓</i><p>RUN_STARTED<small>读取已发布 Agent 配置</small></p><time>0 ms</time></div><div class="trace-row"><i>✓</i><p>RETRIEVAL<small>知识库命中，引用已附加</small></p><time>184 ms</time></div><div class="trace-row"><i>↗</i><p>TOOL_CALL<small>按权限调用已绑定 MCP 工具</small></p><time>428 ms</time></div><div class="trace-row"><i>✓</i><p>RUN_COMPLETED<small>消息、事件与用量已记录</small></p><time>1.2 s</time></div><div class="trace-tags"><span>发布快照</span><span>事件流</span><span>可追溯</span></div></div></div></section>

      <section id="open-source" class="open-source wrap"><div class="open-copy"><p class="kicker">属于你的平台</p><h2>从开源开始，<br>按业务需要成长。</h2><p>模块化单体架构，前后端分离。可在自己的环境部署，并沿清晰的模块边界扩展模型、知识与 Agent 能力。</p><div class="actions"><a class="button button-dark" :href="github" target="_blank" rel="noopener noreferrer">浏览源代码 <span aria-hidden="true">↗</span></a><a class="plain-link" :href="github" target="_blank" rel="noopener noreferrer">阅读项目文档 <span aria-hidden="true">↗</span></a></div></div><div class="open-list"><div><span>01</span><h3>自托管部署</h3><p>支持 PostgreSQL 或 MySQL 业务库；向量检索可选择独立存储实例。</p></div><div><span>02</span><h3>前后端分离</h3><p>管理工作台、对话门户与运行 API 各有入口，便于按场景接入。</p></div><div><span>03</span><h3>持续演进</h3><p>Workflow 已有管理 API 与基础运行模块，可视化编排和完整运行闭环仍在完善。</p></div></div></section>
      <section class="closing"><div class="wrap"><p class="kicker">开始构建</p><h2>把下一款 AI 应用，<br>建立在清晰的基础上。</h2><p>从模型接入到运行观测，探索 v5ai-nb 的开源实现。</p><div class="actions"><a class="button button-light" :href="github" target="_blank" rel="noopener noreferrer">前往 GitHub <span aria-hidden="true">↗</span></a><a class="plain-link" :href="enterprise" target="_blank" rel="noopener noreferrer">了解企业版 <span aria-hidden="true">↗</span></a></div></div></section>
    </main>
    <footer class="footer wrap"><div><a class="brand" href="#top"><span class="brand-mark" aria-hidden="true"></span>v5ai-nb</a><p>面向真实业务的开源 AI 应用平台。</p></div><nav aria-label="页脚导航"><a :href="github" target="_blank" rel="noopener noreferrer">GitHub ↗</a><a :href="documentation" target="_blank" rel="noopener noreferrer">文档 ↗</a><a :href="enterprise" target="_blank" rel="noopener noreferrer">企业版 ↗</a></nav><small>© {{ year }} v5ai-nb</small></footer>
  </div>
</template>
