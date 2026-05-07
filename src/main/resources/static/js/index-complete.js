(() => {
    const CART_STORAGE_KEY = "namhae_cart_v1";
    const TOKEN_STORAGE_KEY = "accessToken";

    function decodeJwtPayload(token) {
        if (!token || typeof token !== "string") {
            return null;
        }

        const parts = token.split(".");
        if (parts.length < 2) {
            return null;
        }

        try {
            const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
            const paddedBase64 = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, "=");
            const json = decodeURIComponent(
                atob(paddedBase64)
                    .split("")
                    .map((char) => `%${char.charCodeAt(0).toString(16).padStart(2, "0")}`)
                    .join("")
            );

            return JSON.parse(json);
        } catch {
            return null;
        }
    }

    function getLoginMemberId() {
        const payload = decodeJwtPayload(localStorage.getItem(TOKEN_STORAGE_KEY));
        return payload?.memberId || payload?.sub || payload?.username || null;
    }

    function updateLoginMemberId() {
        const memberId = getLoginMemberId();
        const targets = document.querySelectorAll("[data-login-member-id]");

        targets.forEach((target) => {
            target.textContent = memberId || "";
            target.classList.toggle("is-hidden", !memberId);
        });
    }

    async function updateMemberWelcome() {
        const welcome = document.getElementById("member-welcome");
        const nameTarget = document.getElementById("member-welcome-name");
        const token = localStorage.getItem(TOKEN_STORAGE_KEY);

        if (!welcome || !nameTarget) {
            return;
        }

        if (!token) {
            welcome.classList.add("is-hidden");
            nameTarget.textContent = "";
            return;
        }

        try {
            const response = await fetch("/api/auth/me", {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            });

            if (!response.ok) {
                welcome.classList.add("is-hidden");
                nameTarget.textContent = "";
                return;
            }

            const member = await response.json();
            const displayName = member.name || member.memberId || getLoginMemberId();

            if (!displayName) {
                welcome.classList.add("is-hidden");
                nameTarget.textContent = "";
                return;
            }

            nameTarget.textContent = displayName;
            welcome.classList.remove("is-hidden");
        } catch {
            welcome.classList.add("is-hidden");
            nameTarget.textContent = "";
        }
    }

    window.getLoginMemberId = getLoginMemberId;

    function loadCartFromStorage() {
        try {
            const raw = localStorage.getItem(CART_STORAGE_KEY);
            if (!raw) {
                return [];
            }

            const parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? parsed : [];
        } catch {
            return [];
        }
    }

    function updateCartBadge() {
        const badges = document.querySelectorAll(".cart-count-badge");
        if (!badges.length) {
            return;
        }

        const totalCount = loadCartFromStorage().reduce((sum, item) => {
            const quantity = Number(item && item.quantity) || 0;
            return sum + quantity;
        }, 0);

        badges.forEach((badge) => {
            badge.textContent = totalCount > 99 ? "99+" : String(totalCount);
            badge.classList.toggle("hidden", totalCount <= 0);
            badge.classList.toggle("is-hidden", totalCount <= 0);
        });
    }

    function updateHeaderBackground() {
        const header = document.querySelector(".site-header");
        if (!header) {
            return;
        }

        if (window.innerHeight < 100) {
            header.style.backgroundColor = "transparent";
            header.style.backdropFilter = "none";
            header.style.borderBottomColor = "transparent";
            header.style.opacity = "0";
            header.style.pointerEvents = "none";
            return;
        }

        header.style.opacity = "1";
        header.style.pointerEvents = "auto";

        if (window.scrollY === 0) {
            header.style.backgroundColor = "transparent";
            header.style.backdropFilter = "none";
            header.style.borderBottomColor = "transparent";
            return;
        }

        header.style.backgroundColor = "rgba(255, 255, 255, 0.1)";
        header.style.backdropFilter = "blur(12px)";
        header.style.borderBottomColor = "rgba(153, 194, 255, 0.2)";
    }

    function setupProductListControls() {
        const initialVisibleCount = 28;
        const additionalVisibleCount = 20;
        const tabButtons = document.querySelectorAll(".tab-btn");
        const productItems = document.querySelectorAll("#products-grid > [data-category]");
        const emptyMessage = document.getElementById("products-empty-message");
        const form = document.getElementById("nav-search-form");
        const input = document.getElementById("nav-search-input");
        const moreButton = document.getElementById("product-more-btn");
        let visibleLimit = initialVisibleCount;

        const initialQuery = new URLSearchParams(window.location.search).get("q");
        if (input && initialQuery) {
            input.value = initialQuery;
        }

        if (!productItems.length) {
            if (moreButton) {
                moreButton.disabled = true;
                moreButton.setAttribute("aria-disabled", "true");
                moreButton.textContent = "더 이상 상품이 없습니다";
            }
            return;
        }

        function getActiveCategory() {
            return document.querySelector(".tab-btn.is-active")?.dataset.category || "all";
        }

        function getSearchQuery() {
            return input ? input.value.trim().toLowerCase() : "";
        }

        function filterProducts() {
            const category = getActiveCategory();
            const query = getSearchQuery();
            let matchedCount = 0;
            let visibleCount = 0;

            productItems.forEach((item) => {
                const productName = item.querySelector(".product-card__name")?.textContent.trim().toLowerCase() || "";
                const matchesCategory = Boolean(query) || category === "all" || item.dataset.category === category;
                const matchesQuery = !query || productName.includes(query);
                const isMatched = matchesCategory && matchesQuery;
                const isVisible = isMatched && matchedCount < visibleLimit;

                item.classList.toggle("is-hidden", !isVisible);

                if (isMatched) {
                    matchedCount += 1;
                }

                if (isVisible) {
                    visibleCount += 1;
                }
            });

            if (emptyMessage) {
                emptyMessage.classList.toggle("is-hidden", visibleCount > 0);
            }

            if (moreButton) {
                const hasMoreProducts = matchedCount > visibleLimit;
                moreButton.disabled = !hasMoreProducts;
                moreButton.setAttribute("aria-disabled", String(!hasMoreProducts));
                moreButton.textContent = hasMoreProducts ? "상품 더 보기" : "더 이상 상품이 없습니다";
            }
        }

        tabButtons.forEach((button) => {
            button.addEventListener("click", () => {
                visibleLimit = initialVisibleCount;
                tabButtons.forEach((btn) => btn.classList.remove("is-active"));
                button.classList.add("is-active");
                filterProducts();
            });
        });

        if (form) {
            // 검색어가 있으면 별도 페이지(/search)로 이동, 빈 검색어 시 기존 in-place 필터 유지
            form.addEventListener("submit", (event) => {
                const q = input ? input.value.trim() : "";
                if (!q) {
                    event.preventDefault();
                    visibleLimit = initialVisibleCount;
                    filterProducts();
                    document.getElementById("best")?.scrollIntoView({ behavior: "smooth", block: "start" });
                }
            });
        }

        if (input) {
            input.addEventListener("input", () => {
                visibleLimit = initialVisibleCount;
                filterProducts();
            });
        }

        if (moreButton) {
            moreButton.addEventListener("click", () => {
                visibleLimit += additionalVisibleCount;
                filterProducts();
            });
        }

        filterProducts();
    }

    function setupTrendKeywordMoreButton() {
        const button = document.getElementById("trend-keyword-more-button");
        const extraKeywords = document.querySelectorAll(".trend-keyword-card--extra");

        if (!button || !extraKeywords.length) {
            return;
        }

        button.addEventListener("click", () => {
            const isExpanded = button.dataset.expanded === "true";

            extraKeywords.forEach((item) => {
                item.classList.toggle("is-hidden", isExpanded);
            });

            button.dataset.expanded = String(!isExpanded);
            button.textContent = isExpanded ? "\uB354\uBCF4\uAE30" : "\uB2EB\uAE30";
        });
    }

    function setupTrendKeywordImages() {
        const imageRules = [
            {
                words: ["담두만두", "만두", "mandu", "dumpling"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Korean_mandu_dumplings.jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Korean_Chinese_mandu.jpg"
                ]
            },
            {
                words: ["유제품", "우유", "요거트", "요구르트", "치즈", "milk", "dairy", "yogurt", "cheese"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Dairy.jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Llaeth_y_Llan,_Village_Dairy_yogurt.jpg"
                ]
            },
            {
                words: ["두릅", "dureup", "aralia"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Dureup_(Korean_angelica-tree_shoots).jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Dureup_2.jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Blanched_dureup.jpg"
                ]
            },
            {
                words: ["생선", "멸치", "전복", "굴", "조개", "새우", "미역", "김", "해물", "해산", "수산", "fish", "seafood"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Jagalchi_Fish_Market.jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Korea-Busan-Jagalchi_Fish_Market-01.jpg"
                ]
            },
            {
                words: ["시금치", "상추", "채소", "야채", "고추", "마늘", "양파", "깻잎", "나물", "샐러드", "spinach", "vegetable", "salad"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Fresh_Vegetables_2.jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Fresh_veggies.jpg"
                ]
            },
            {
                words: ["쌀", "밥", "현미", "찹쌀", "잡곡", "rice"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Rice_grains_(IRRI).jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Rice_grains.jpg"
                ]
            },
            {
                words: ["사과", "배", "딸기", "귤", "감", "포도", "복숭아", "블루베리", "토마토", "과일", "fruit", "tomato"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Various_fruits.jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Strawberries.jpg"
                ]
            },
            {
                words: ["빵", "쿠키", "케이크", "떡", "베이커", "bread", "bakery", "cake"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Bread_loaf.jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Freshly_baked_bread_loaves.jpg"
                ]
            },
            {
                words: ["커피", "차", "음료", "주스", "coffee", "tea", "drink", "juice"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/A_small_cup_of_coffee.JPG",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Orange_Juice_(13638538444).jpg"
                ]
            },
            {
                words: ["선물", "세트", "gift", "set"],
                urls: [
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Gift_box.jpg",
                    "https://commons.wikimedia.org/wiki/Special:Redirect/file/Exotic_Fruit_Gift_Basket_(4461109309).jpg"
                ]
            }
        ];
        const products = Array.from(document.querySelectorAll("#products-grid > .product-item")).map((item) => {
            const image = item.querySelector(".product-card__image");
            const name = item.querySelector(".product-card__name")?.textContent.trim() || "";

            return {
                category: item.dataset.category || "agricultural",
                imageUrl: image?.getAttribute("src") || image?.currentSrc || "",
                name
            };
        }).filter((product) => product.imageUrl);
        const fallbackUrls = [
            "https://commons.wikimedia.org/wiki/Special:Redirect/file/Fresh_Vegetables_2.jpg",
            "https://commons.wikimedia.org/wiki/Special:Redirect/file/Korean_mandu_dumplings.jpg",
            "https://commons.wikimedia.org/wiki/Special:Redirect/file/Dairy.jpg",
            "https://commons.wikimedia.org/wiki/Special:Redirect/file/Aralia_elata_(2).jpg"
        ];
        const usedUrls = new Set();

        function normalize(value) {
            return String(value || "").replace(/\s+/g, "").toLowerCase();
        }

        function pickUnused(candidates, fallbackUrl = "") {
            const validCandidates = candidates.filter(Boolean);
            return validCandidates.find((url) => !usedUrls.has(url)) || fallbackUrl || validCandidates[0] || "";
        }

        document.querySelectorAll("[data-keyword-image]").forEach((image) => {
            const rawKeyword = image.dataset.keyword || "";
            const keyword = normalize(rawKeyword);
            const preferProductImage = ["가죽나물", "쌀국수", "명가삼대떡집"].some((word) => keyword.includes(normalize(word)));
            const matchedRule = imageRules.find((rule) => rule.words.some((word) => keyword.includes(normalize(word))));
            const matchedProducts = products.filter((product) => {
                const productName = normalize(product.name);
                return productName && keyword && (productName.includes(keyword) || keyword.includes(productName));
            });
            const keywordFallbackUrl = `https://placehold.co/640x480/e6f3ff/0066cc?text=${encodeURIComponent(rawKeyword || "Food")}`;
            const imageCandidates = preferProductImage
                ? [
                    ...matchedProducts.map((product) => product.imageUrl),
                    ...(matchedRule?.urls || []),
                    ...products.map((product) => product.imageUrl),
                    ...fallbackUrls
                ]
                : [
                    ...(matchedRule?.urls || []),
                    ...matchedProducts.map((product) => product.imageUrl),
                    ...products.map((product) => product.imageUrl),
                    ...fallbackUrls
                ];
            const imageUrl = pickUnused(imageCandidates, keywordFallbackUrl);

            if (imageUrl) {
                image.src = imageUrl;
                usedUrls.add(imageUrl);
            }

            image.alt = image.dataset.keyword ? `${image.dataset.keyword} 이미지` : "";
        });
    }

    function setupTrendKeywordAuthLinks() {
        document.querySelectorAll(".js-trend-keyword-link").forEach((link) => {
            link.addEventListener("click", (event) => {
                if (localStorage.getItem(TOKEN_STORAGE_KEY)) {
                    return;
                }

                event.preventDefault();
                window.location.href = "/login.html";
            });
        });
    }

    function setupScrollTopButton() {
        const button = document.getElementById("scroll-top-btn");
        if (!button) {
            return;
        }

        function updateVisibility() {
            button.classList.toggle("is-visible", window.scrollY > 360);
        }

        button.addEventListener("click", () => {
            window.scrollTo({
                top: 0,
                behavior: "smooth"
            });
        });

        updateVisibility();
        window.addEventListener("scroll", updateVisibility, { passive: true });
    }

    function init() {
        updateCartBadge();
        updateLoginMemberId();
        updateMemberWelcome();
        setupProductListControls();
        setupTrendKeywordMoreButton();
        setupTrendKeywordImages();
        setupTrendKeywordAuthLinks();
        setupScrollTopButton();
        updateHeaderBackground();

        window.addEventListener("scroll", updateHeaderBackground, { passive: true });
        window.addEventListener("resize", updateHeaderBackground);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }
})();
