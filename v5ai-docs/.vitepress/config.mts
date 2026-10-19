import { defineConfig } from 'vitepress';

export default defineConfig({
  lang: 'zh-CN',
  title: 'v5ai-nb 文档',
  description: 'v5ai-nb 开源 AI 应用平台：从安装部署到 Agent、知识库、MCP、Skill 与运行 API。',
  cleanUrls: true,
  lastUpdated: false,
  sitemap: { hostname: 'https://ai-docs.v5ai.xin' },
  head: [
    ['meta', { name: 'theme-color', content: '#faf9f7' }],
    ['link', { rel: 'icon', href: '/logo.svg', type: 'image/svg+xml' }],
  ],
  themeConfig: {
    logo: '/logo.svg',
    siteTitle: 'v5ai-nb 文档',
    nav: [
      { text: '首页', link: '/' },
      { text: '快速上手', link: '/zh/start' },
      { text: '使用指南', link: '/zh/guides/agents' },
      { text: 'API', link: '/zh/reference/api' },
      { text: '部署', link: '/zh/deploy/local' },
      { text: '官网', link: 'https://ai.v5ai.xin' },
    ],
    sidebar: {
      '/zh/': [
        { text: '开始', items: [
          { text: '快速上手', link: '/zh/start' },
          { text: '平台概念与架构', link: '/zh/concepts' },
        ] },
        { text: '构建 AI 应用', items: [
          { text: '模型与供应商', link: '/zh/guides/models' },
          { text: '创建与发布 Agent', link: '/zh/guides/agents' },
          { text: '知识库与 RAG', link: '/zh/guides/knowledge' },
          { text: '连接 MCP 工具', link: '/zh/guides/mcp' },
          { text: '编写与发布 Skill', link: '/zh/guides/skills' },
          { text: 'Workflow 工作流', link: '/zh/guides/workflows' },
          { text: '对话门户', link: '/zh/guides/chat-portal' },
          { text: '调试与观测', link: '/zh/guides/observability' },
        ] },
        { text: '开发参考', items: [
          { text: 'API 与鉴权', link: '/zh/reference/api' },
          { text: '配置与环境变量', link: '/zh/reference/configuration' },
          { text: '源码与设计文档索引', link: '/zh/reference/sources' },
        ] },
        { text: '部署与维护', items: [
          { text: '本地开发部署', link: '/zh/deploy/local' },
          { text: 'Docker Compose 部署', link: '/zh/deploy/docker' },
          { text: '常见问题', link: '/zh/help/troubleshooting' },
        ] },
      ],
    },
    search: {
      provider: 'local',
      options: {
        locales: {
          root: {
            translations: {
              button: { buttonText: '搜索文档', buttonAriaLabel: '搜索文档' },
              modal: {
                displayDetails: '显示详细结果',
                resetButtonTitle: '清空搜索',
                backButtonTitle: '关闭搜索',
                noResultsText: '没有找到结果',
                footer: {
                  selectText: '选择', selectKeyAriaLabel: '回车',
                  navigateText: '切换', navigateUpKeyAriaLabel: '向上',
                  navigateDownKeyAriaLabel: '向下', closeText: '关闭', closeKeyAriaLabel: 'Esc',
                },
              },
            },
          },
        },
      },
    },
    socialLinks: [{ icon: 'github', link: 'https://github.com/zyw/v5ai-nb' }],
    outline: [2, 3],
    outlineTitle: '本页目录',
    docFooter: { prev: '上一页', next: '下一页' },
    sidebarMenuLabel: '目录',
    returnToTopLabel: '返回顶部',
    darkModeSwitchLabel: '主题',
    footer: { message: '开源 AI 应用平台', copyright: '© 2026 v5ai-nb' },
  },
});
