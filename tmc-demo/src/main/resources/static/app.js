const productList = document.querySelector("#productList");
const seedButton = document.querySelector("#seedButton");
const trafficButton = document.querySelector("#trafficButton");
const refreshButton = document.querySelector("#refreshButton");
const statusText = document.querySelector("#statusText");
const toast = document.querySelector("#toast");

/**
 * 当前页面选中的热点商品。Demo 默认用 iPhone 商品制造热点访问，
 * 这样 Grafana 中的 tmc_client_* 指标更容易快速出现变化。
 */
let selectedProductId = "1001";

/**
 * 页面启动入口。
 */
document.addEventListener("DOMContentLoaded", async () => {
    bindActions();
    await loadProducts();
    await loadMetrics();
});

/**
 * 绑定页面按钮事件。所有按钮只调用业务 API，不直接触碰 TMC SDK。
 */
function bindActions() {
    seedButton.addEventListener("click", async () => {
        await postJson("/api/flash-sale/seed");
        showToast("商品已初始化");
        await loadProducts();
        await loadMetrics();
    });

    trafficButton.addEventListener("click", async () => {
        statusText.textContent = "正在制造热点访问...";
        const result = await postJson(`/api/flash-sale/traffic/hot-product/${selectedProductId}?count=800`);
        showToast(`已读取 ${result.successCount} 次`);
        statusText.textContent = `热点商品 ${result.productId} 已完成 ${result.successCount} 次读取`;
        await loadMetrics();
    });

    refreshButton.addEventListener("click", async () => {
        await loadProducts();
        await loadMetrics();
        showToast("已刷新");
    });
}

/**
 * 拉取商品列表。读取接口会逐个访问 Redis 商品 key，并经过 TmcJedis 透明读路径。
 */
async function loadProducts() {
    const products = await getJson("/api/flash-sale/products");
    if (!products.length) {
        productList.innerHTML = `<div class="empty-state">暂无商品</div>`;
        return;
    }
    productList.innerHTML = products.map(renderProduct).join("");
    productList.querySelectorAll("[data-buy]").forEach(button => {
        button.addEventListener("click", () => purchase(button.dataset.buy));
    });
}

/**
 * 渲染单个抢购商品。
 */
function renderProduct(product) {
    const disabled = product.remainCount <= 0 ? "disabled" : "";
    return `
        <article class="product-card">
            <div class="image-box">
                <img src="${product.imageUrl}" alt="${product.name}">
            </div>
            <div class="product-copy">
                <h2 class="product-name"><span class="limit-tag">限量</span>${product.name}</h2>
                <p class="product-desc">${product.description}</p>
                <div class="stock-row">
                    <span>已抢 ${product.soldCount} 件</span>
                    <span>仅剩 ${product.remainCount} 件</span>
                </div>
                <div class="progress" aria-label="售卖进度 ${product.progressPercent}%">
                    <span style="width: ${product.progressPercent}%"></span>
                </div>
                <div class="tag-row">
                    <span>${product.installmentLabel}</span>
                    <span>顺丰包邮</span>
                </div>
            </div>
            <div class="price-action">
                <div class="price"><small>￥</small>${product.price}<del>￥${product.originalPrice}</del></div>
                <button class="buy-button" type="button" data-buy="${product.id}" ${disabled}>立即抢购</button>
            </div>
        </article>
    `;
}

/**
 * 执行抢购写操作。后端写 Redis 成功后，TmcJedis 会通知 SDK 失效本地缓存。
 */
async function purchase(productId) {
    selectedProductId = productId;
    try {
        const result = await postJson(`/api/flash-sale/products/${productId}/purchase`);
        showToast(result.message);
        await loadProducts();
        await loadMetrics();
    } catch (error) {
        showToast(error.message);
    }
}

/**
 * 拉取 SDK 即时指标快照，方便本地页面快速观察读链路变化。
 */
async function loadMetrics() {
    const metrics = await getJson("/api/flash-sale/metrics");
    document.querySelector("#totalGets").textContent = metrics.totalGets ?? 0;
    document.querySelector("#redisGets").textContent = metrics.redisGets ?? 0;
    document.querySelector("#localHits").textContent = metrics.localCacheHits ?? 0;
    document.querySelector("#invalidations").textContent = metrics.localInvalidations ?? 0;
}

async function getJson(url) {
    const response = await fetch(url);
    return readJsonResponse(response);
}

async function postJson(url) {
    const response = await fetch(url, {method: "POST"});
    return readJsonResponse(response);
}

async function readJsonResponse(response) {
    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || "请求失败");
    }
    return data;
}

function showToast(message) {
    toast.textContent = message;
    toast.classList.add("show");
    window.clearTimeout(showToast.timer);
    showToast.timer = window.setTimeout(() => toast.classList.remove("show"), 1800);
}
