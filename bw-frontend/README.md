## 一个入门简单、跨平台、企业级桌面软件开发框架

### 分支说明
* 该分支为模板分支，新项目可以在此基础上拉取新分支后进行相关功能开发；
* 该分支只会更新基础功能与示例；
* 不建议新功能开发时在此分支上进行，而是新建功能分支进行开发，开发完毕后合并至该分支；
* 该模板可进行WEB、DESKTOP两类程序开发，可根据项目类型进行选择；
  * WEB：只用关注 ``` ./frontend ``` 文件夹下的内容，与传统WEB开发无区别；
  * DESKTOP：除 ``` ./frontend ``` 文件夹下的WEB开发传统内容以外，还需要关注 ``` ./frontend/src/electron ``` 文件夹与 
  ``` ./frontend/src/ToolsBar.vue ``` 文件；

### 开发说明
* 环境安装
  * 解压根目录下 ``` Local.zip ``` 至 ``` C:\Users\{当前用户}\AppData\Local ``` 文件夹下;
  * 解压根目录下 ``` node_modules.zip ``` 与 ``` frontend ``` 文件下 ``` node_modules.zip ``` 至当前目录;

* 配置文件
  * ``` ./electron/config ``` 目录下是所有配置文件
  ```
  bin.json          // 开发配置
  config.default.js // 默认配置文件，开发环境和生产环境都会加载
  config.local.js   // 开发环境配置文件，追加和覆盖default配置文件
  config.prod.js    // 生产环境配置文件，追加和覆盖default配置文件
  encrypt.js        // 代码加密的配置
  nodemon.json      // 开发环境，代码（监控）热加载
  builder.json      // 打包配置
  ```

* 修改名称
  * ``` ./package.json ```
    * name:项目名称
  * ``` ./electron/config/builder.json ```
    * productName:可执行程序名称（不能中文）
    * appId:软件id
    * shortcutName:桌面快捷方式名称

* 程序运行
  * 检查 ``` ./build/extraResources ``` 文件夹下是否有运行所需资源文件;
  * 根目录下打开命令行;
  * 运行 ``` npm run dev ``` ;

* 程序打包
  * 根目录下打开命令行
  * 运行 ``` npm run build-frontend ``` 打包前端文件至 ``` ./public/dist ``` 文件夹下;
  * 运行 ``` npm run encrypt ``` 进行electron代码加密;
  * 根据当前操作系统平台运行下方指令进行打包;
    ``` 
      #打包 （windows版）
      npm run build-w
      npm run build-w-32 (32位)
      npm run build-w-64 (64位)
      npm run build-w-arm64 (arm64)
  
      # 打包 （windows 免安装版）
      npm run build-wz
      npm run build-wz-32 (32位)
      npm run build-wz-64 (64位)
      npm run build-wz-arm64 (arm64)
    
      # 打包 （mac版）
      npm run build-m
      npm run build-m-arm64 (m1芯片架构)
    
      # 打包 （linux版）
      npm run build-l (32位 deb包)
      npm run build-l-64 (64位 deb包)
      npm run build-l-arm64 (64位 deb包 arm64)
      npm run build-l-armv7l (64位 deb包 armv7l)
      npm run build-lr-64 (64位 rpm包)
      npm run build-lp-64 (64位 pacman包) 
  
      Ubuntu 打deb包
      UOS统信 打deb包
      Debian 打deb包
      Centos 打rpm包
      银河麒麟v10(sp1) 打deb包
    ```
  * 打开根目录 ``` ./out ``` 文件夹查看打包好的程序;

* 注意事项
  * 框架自带文件服务功能，服务端口可修改 ``` ./electron/addon/db/index.js ``` 中的 ``` this.httpPort = 8000 ``` 进行自定义，
  文件服务指向 ``` ./build/extraResources ``` 路径;
  * 后端自带基础能力可在 ``` ./electron/controller/* ``` 与 ``` ./electron/service/* ``` 中查看;