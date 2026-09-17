module.exports = {
  root: true,
  env: {
    node: true
  },
  'extends': [
    'plugin:vue/essential',
    'plugin:prettier/recommended', // 添加 prettier 插件, 注意需要放到 plugin 最后一个
    '@vue/prettier',
  ],
  // rules 里面的内容根据团队风格统一配置
  rules: {
    'no-console': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    // 防死 import 回潮（如 g2plot log 事件）：存量违规较多，先 warn 观察
    'no-unused-vars': 'warn'
  },
  parserOptions: {
    parser: 'babel-eslint'
  }
}