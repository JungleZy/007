麒麟 ARM64 安装诊断（不安装、不卸载、不下载软件包）

1. 将本压缩包完整解压。
2. 把 kylin-install-check.sh 放到 .deb 安装包所在目录。
3. 在该目录打开终端，运行：

bash ./kylin-install-check.sh ./nip-traffic-system-kylin-arm64_3.1.1_arm64.deb

4. 将生成的 kylin-install-report.txt 发回来。

也可以传入 .deb 的完整路径，路径有空格时加引号。
无需 sudo，不要使用 --force-depends，也不要手工替换系统 glibc。
检查内容为系统版本、CPU/包架构、glibc和相关依赖版本、磁盘空间、安装包校验值和模拟安装输出。
不会读取数据库口令、账号或业务数据。
