#!/bin/sh

# 定义源路径 (镜像里自带的)
SOURCE_DIR="/app/lua_dist"
# 定义目标路径 (挂载到宿主机的)
TARGET_DIR="/app/lua_scripts"

echo "检查 Lua 脚本..."

# 如果目标目录不存在 damage_formulas.lua，则从镜像中复制
if [ ! -f "$TARGET_DIR/damage_formulas.lua" ]; then
    echo "从镜像中提取 Lua 脚本..."
    # 确保目录存在
    mkdir -p "$TARGET_DIR"
    # 复制文件
    cp -r "$SOURCE_DIR/"* "$TARGET_DIR/"
    echo "Lua 脚本已复制到 $TARGET_DIR"
else
    echo "Lua 脚本未找到，跳过初始化"
fi

# 启动 Java 应用
# "$@" 代表 Dockerfile CMD 里的参数
echo "Java 应用启动……"
exec "$@"