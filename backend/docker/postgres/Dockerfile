# =====================================================================
# PostgreSQL 镜像：pgvector + zhparser（中文全文检索）
#
# 基础镜像 pgvector/pgvector:pg17 已内置 vector 扩展（1024 维 embedding 可直接用）；
# zhparser 依赖 SCWS 分词库，且新版 zhparser Makefile 不再自动下载 SCWS，
# 因此这里显式编译安装 SCWS（源码取自官方仓库 hightman/scws，1.2.3）。
#
# 关键点（踩坑记录）：
#   - zhparser Makefile 优先用 pkg-config 探测 scws，失败则回退 /usr/local，故须装 pkg-config 并 ldconfig；
#   - hightman/scws 仓库未提交 configure 脚本，需 autotools（autoreconf -i）现场生成；
#   - zhparser 自带词典（dict.utf8.xdb / rules.utf8.ini），随 make install 装到 tsearch_data，无需额外处理。
# =====================================================================
FROM pgvector/pgvector:pg17

ARG SCWS_VERSION=1.2.3
ARG ZHPARSER_REF=master

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        git \
        pkg-config \
        autoconf \
        automake \
        libtool \
        m4 \
        postgresql-server-dev-17; \
    # ---------- ① SCWS 中文分词库 ----------
    git clone --depth 1 --branch "${SCWS_VERSION}" https://github.com/hightman/scws.git /tmp/scws; \
    cd /tmp/scws; \
    (test -x ./configure || autoreconf -i); \
    ./configure --prefix=/usr/local; \
    make -j"$(nproc)"; \
    make install; \
    echo "/usr/local/lib" > /etc/ld.so.conf.d/scws.conf; \
    ldconfig; \
    # ---------- ② zhparser 扩展 ----------
    git clone --depth 1 --branch "${ZHPARSER_REF}" https://github.com/amutu/zhparser.git /tmp/zhparser; \
    cd /tmp/zhparser; \
    make; \
    make install; \
    rm -rf /tmp/scws /tmp/zhparser /var/lib/apt/lists/*

# 构建期自检：zhparser.so、扩展控制文件与 SCWS 库必须存在，否则镜像视为构建失败
RUN set -eux; \
    test -f "$(pg_config --pkglibdir)/zhparser.so"; \
    test -f "$(pg_config --sharedir)/extension/zhparser.control"; \
    test -f "$(pg_config --sharedir)/tsearch_data/dict.utf8.xdb"; \
    test -f /usr/local/include/scws/scws.h; \
    ldconfig -p | grep -q libscws; \
    echo "zhparser + scws OK"