// monaco-editor 0.56 的 editor.main 入口无独立类型声明，
// 其导出形状与 editor.api 一致（额外副作用：注册全部语言与编辑器特性），直接复用其类型
declare module 'monaco-editor/editor/editor.main' {
  export * from 'monaco-editor/editor/editor.api'
}
