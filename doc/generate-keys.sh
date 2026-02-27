#!/bin/bash

# =============================================================================
# RSA 密钥对生成脚本
# 生成 PEM 格式的 RSA 2048 位密钥对
# =============================================================================

set -e

# 输出目录
OUTPUT_DIR="./keys"
mkdir -p "$OUTPUT_DIR"

PRIVATE_KEY="$OUTPUT_DIR/private_key.pem"
PUBLIC_KEY="$OUTPUT_DIR/public_key.pem"

echo "====================================="
echo "  RSA 密钥对生成工具"
echo "====================================="
echo ""

# 检查 openssl
if ! command -v openssl &> /dev/null; then
    echo "错误: 未找到 openssl 命令，请先安装 openssl"
    exit 1
fi

# 检查是否已存在
if [ -f "$PRIVATE_KEY" ] || [ -f "$PUBLIC_KEY" ]; then
    echo "警告: 密钥文件已存在！"
    echo "  私钥: $PRIVATE_KEY"
    echo "  公钥: $PUBLIC_KEY"
    read -p "是否覆盖？(y/N): " confirm
    if [ "$confirm" != "y" ] && [ "$confirm" != "Y" ]; then
        echo "已取消"
        exit 0
    fi
fi

# 生成私钥（PKCS8 格式，Java 可直接读取）
echo "1. 生成 RSA 2048 位私钥..."
openssl genpkey -algorithm RSA -out "$PRIVATE_KEY" -pkeyopt rsa_keygen_bits:2048
echo "   ✅ 私钥已生成: $PRIVATE_KEY"

# 从私钥中导出公钥
echo "2. 导出公钥..."
openssl rsa -pubout -in "$PRIVATE_KEY" -out "$PUBLIC_KEY"
echo "   ✅ 公钥已生成: $PUBLIC_KEY"

echo ""
echo "====================================="
echo "  密钥对生成完成！"
echo "====================================="
echo ""
echo "文件说明："
echo "  📁 $PRIVATE_KEY  → 放到 license-server 端（严格保密！）"
echo "  📁 $PUBLIC_KEY   → 放到客户端/license-demo 端"
echo ""
echo "使用方式："
echo "  1. 将 keys/ 目录复制到 license-server 和 license-demo 的运行目录下"
echo "  2. 或通过环境变量指定路径："
echo "     export LICENSE_PRIVATE_KEY_PATH=$PRIVATE_KEY"
echo "     export LICENSE_PUBLIC_KEY_PATH=$PUBLIC_KEY"
echo ""
echo "⚠️  请妥善保管私钥文件，切勿泄露！"
