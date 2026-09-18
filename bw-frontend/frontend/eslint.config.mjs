/**
 * ESLint 扁平配置。
 *
 * 为什么不是 `.eslintrc.js`：仓库里装着的 ESLint 是 v10，自 v9 起默认只读 `eslint.config.*`，
 * v10 已彻底移除 eslintrc 支持——旧 `.eslintrc.js` 实跑会直接报
 * `ESLint couldn't find an eslint.config.* file`。而且它 extends 的三条链
 * （plugin:vue/essential、plugin:prettier/recommended、@vue/prettier）对应的包从未装进
 * devDependencies，所以 2026-09-17 那轮给 no-unused-vars 加的「防回潮」规则一天都没生效过。
 *
 * 当前门槛：全部 warn，不设失败闸。理由是先取一次可信基线，再按域逐批降清零、逐条升 error；
 * 一上来就 error 会让 `npm run lint` 立刻不可用，等于又变成一条没人跑的规则。
 * 升级路径见 docs/reviews/2026-09-18-frontend-consistency-redundancy-review.md 档 2。
 *
 * 不接 prettier：`prettierrc.js`（缺前导点，从未生效）已删除，本次不引入格式化。
 * 全量格式化会一次性改动数百个文件、淹没所有 review 信号，需要单独立项。
 */
import globals from 'globals'
import pluginVue from 'eslint-plugin-vue'

export default [
  {
    ignores: [
      'dist/**',
      'node_modules/**',
      'public/**',
      // vendored 第三方库，不属本仓代码风格治理范围
      'src/common/mqtt/paho-mqtt.js'
    ]
  },
  // vue/essential 与旧 .eslintrc.js 的 extends 档位一致：只收真错，不收风格偏好
  ...pluginVue.configs['flat/essential'],
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        // index.html 以 <script> 注入的 vendored 全局（public/js/animejs），
        // 不是本仓模块，声明出来免得 no-undef 把它当噪音（47 处使用）
        anime: 'readonly'
      }
    },
    rules: {
      // 防死 import / 死变量回潮（原 .eslintrc.js 的意图，此处首次真正生效）
      'no-unused-vars': ['warn', { args: 'none', ignoreRestSiblings: true }],
      'no-undef': 'warn',
      'no-debugger': 'warn',
      // console.error / console.warn 是本仓唯一的故障上报通道（授权、串口、会话保存、
      // PubSub 订阅者异常等 27 处都靠它），删掉等于制造静默失败，故显式放行；
      // console.log / time 属调试残留，保持告警。生产构建另有 terser drop_console 兜底。
      'no-console': ['warn', { allow: ['error', 'warn'] }],
      // 关掉：本仓的路由组件文件名与后端 t_menus.component 路径绑定（guards.js 按路径匹配），
      // 上百个 Index.vue 不是风格随意，改名必须同步菜单表，属评审档 5 的门控项。
      // 留着它只会产生 67 条永远不会被处理的噪音，反而盖住真缺陷。
      'vue/multi-word-component-names': 'off'
    }
  },
  {
    // 一次性脚本与 node 测试跑在 node 里，允许 console
    files: ['test/**/*.mjs', '**/*.test.mjs', '*.config.{js,mjs,cjs}'],
    rules: {
      'no-console': 'off'
    }
  },
  {
    // components/style/*.vue 是「皮肤样式载体」：模板故意为空，全部内容在 <style> 里，
    // 由 GlobalStyle.vue 按 window.interfaceStyle 动态 import 后用 <component :is> 挂载。
    // valid-template-root 要求模板必须有子元素，对这个模式属误报；
    // 删掉空模板反而会触发 Vue「缺少 template 或 render」的运行时告警。
    files: ['src/components/style/*.vue'],
    rules: {
      'vue/valid-template-root': 'off'
    }
  }
]
