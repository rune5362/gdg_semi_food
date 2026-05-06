// Admin Dashboard JavaScript

let selectedOrderId = null;

// Mobile menu toggle functionality
function initMobileMenu() {
    const mobileMenuToggle = document.getElementById('mobileMenuToggle');
    const sidebar = document.querySelector('.sidebar');
    const page = document.querySelector('.page');
    const overlay = document.querySelector('.mobile-menu-overlay');
    
    if (mobileMenuToggle && sidebar && page) {
        mobileMenuToggle.addEventListener('click', function() {
            const isOpen = sidebar.classList.contains('open');
            
            if (isOpen) {
                sidebar.classList.remove('open');
                page.classList.remove('sidebar-closed');
                mobileMenuToggle.classList.remove('active');
                if (overlay) overlay.classList.remove('active');
            } else {
                sidebar.classList.add('open');
                page.classList.add('sidebar-closed');
                mobileMenuToggle.classList.add('active');
                if (overlay) overlay.classList.add('active');
            }
        });
        
        // Close menu when clicking overlay
        if (overlay) {
            overlay.addEventListener('click', function() {
                sidebar.classList.remove('open');
                page.classList.remove('sidebar-closed');
                mobileMenuToggle.classList.remove('active');
                overlay.classList.remove('active');
            });
        }
        
        // Close menu when window is resized to desktop
        window.addEventListener('resize', function() {
            if (window.innerWidth > 768) {
                sidebar.classList.remove('open');
                page.classList.remove('sidebar-closed');
                mobileMenuToggle.classList.remove('active');
                if (overlay) overlay.classList.remove('active');
            }
        });
    }
}

// Load orders on page load
document.addEventListener('DOMContentLoaded', function () {
    initMobileMenu();
    loadCurrentAdminProfile();

    // Check if we're on the orders page
    if (document.getElementById('orderItems')) {
        loadOrders();
        
        // Search functionality
        const orderSearch = document.getElementById('orderSearch');
        if (orderSearch) {
            orderSearch.addEventListener('input', function (e) {
                const searchTerm = e.target.value.toLowerCase();
                const items = document.querySelectorAll('.order-item');

                items.forEach(item => {
                    const orderNumber = item.querySelector('.order-id').textContent.toLowerCase();
                    const customerName = item.querySelector('.order-name').textContent.toLowerCase();

                    if (orderNumber.includes(searchTerm) || customerName.includes(searchTerm)) {
                        item.style.display = 'block';
                    } else {
                        item.style.display = 'none';
                    }
                });
            });
        }
    }
    
    // Check if we're on the dashboard page
    if (document.getElementById('totalProducts')) {
        loadDashboardData();
    }
    
    // Check if we're on the members page
    if (document.getElementById('memberGrid')) {
        loadMembers();
        
        // Search functionality for members
        const searchInput = document.getElementById('searchInput');
        if (searchInput) {
            searchInput.addEventListener('input', function (e) {
                const searchTerm = e.target.value.toLowerCase();
                const cards = document.querySelectorAll('.member-card');

                cards.forEach(card => {
                    const name = card.querySelector('.member-name').textContent.toLowerCase();
                    const email = card.querySelector('.member-email').textContent.toLowerCase();

                    if (name.includes(searchTerm) || email.includes(searchTerm)) {
                        card.style.display = 'flex';
                    } else {
                        card.style.display = 'none';
                    }
                });
            });
        }
        
        // Modal overlay click to close
        document.querySelectorAll('.modal-overlay').forEach(overlay => {
            overlay.addEventListener('click', function (e) {
                if (e.target === overlay) {
                    overlay.classList.remove('open');
                }
            });
        });
    }
    
    // Check if we're on the products page
    if (document.getElementById('productGrid')) {
        loadProducts();
        
        // Infinite scroll removed - using pagination instead
        
        // Search functionality for products
        const productSearch = document.getElementById('productSearch');
        if (productSearch) {
            productSearch.addEventListener('input', function (e) {
                const searchTerm = e.target.value.toLowerCase();
                const cards = document.querySelectorAll('.product-card');

                // Real-time filtering for displayed products
                cards.forEach(card => {
                    const name = card.querySelector('.product-title').textContent.toLowerCase();
                    const description = card.querySelector('.detail-value.price') ? 
                        card.querySelector('.detail-value.price').textContent.toLowerCase() : '';

                    if (name.includes(searchTerm) || description.includes(searchTerm)) {
                        card.style.display = 'flex';
                    } else {
                        card.style.display = 'none';
                    }
                });
            });

            // Enter key for DB search
            productSearch.addEventListener('keypress', function (e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    const searchTerm = e.target.value.trim();
                    // Reset search term if it's different from current
                    if (searchTerm !== currentSearchTerm) {
                        currentSearchTerm = null;
                    }
                    searchProductsFromDB(searchTerm);
                }
            });
        }
        
        // Modal overlay click to close
        document.querySelectorAll('.modal-overlay').forEach(overlay => {
            overlay.addEventListener('click', function (e) {
                if (e.target === overlay) {
                    overlay.classList.remove('open');
                }
            });
        });
    }
});

function loadCurrentAdminProfile() {
    const nameElement = document.getElementById('sidebarUserName');
    const emailElement = document.getElementById('sidebarUserEmail');
    const sidebarInitialElement = document.getElementById('sidebarUserInitial');
    const topbarInitialElement = document.getElementById('topbarUserInitial');

    if (!nameElement || !emailElement || !sidebarInitialElement) {
        return;
    }

    fetch('/api/auth/me')
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to load current user');
            }
            return response.json();
        })
        .then(member => {
            const displayName = member.name || member.memberId || 'Admin';
            const displayEmail = member.email || 'admin@dadream.com';
            const displayInitial = displayName.trim() ? displayName.trim().substring(0, 1).toUpperCase() : 'A';

            nameElement.textContent = displayName;
            emailElement.textContent = displayEmail;
            sidebarInitialElement.textContent = displayInitial;
            
            // Update topbar avatar if it exists
            if (topbarInitialElement) {
                topbarInitialElement.textContent = displayInitial;
            }
        })
        .catch(error => {
            console.error('Error loading current admin profile:', error);
        });
}

// Dashboard functions
function loadDashboardData() {
    fetch('/api/admin/dashboard/stats')
        .then(response => response.json())
        .then(data => {
            updateStats(data);
            updateKeywords(data.keywords);
            updateWordCloud(data.keywords);
            updateRecentLogs(data.recentLogs || []);
            updateRpaExtractionStatus(data.rpaExtractionData || []);
        })
        .catch(error => {
            console.error('Error loading dashboard data:', error);
            showToast('Failed to load dashboard data');
            
            // Load mock data if API fails
            loadMockDashboardData();
        });
}

function loadMockDashboardData() {
    const mockData = {
        totalProducts: 48,
        totalMembers: 156,
        totalSales: 892,
        totalRevenue: 45678000,
        keywords: [
            { rank: 1, name: 'Organic Cherry Tomatoes', percentage: 85, frequency: 95 },
            { rank: 2, name: 'Fresh Avocado', percentage: 72, frequency: 82 },
            { rank: 3, name: 'Premium Mango', percentage: 68, frequency: 76 },
            { rank: 4, name: 'Mixed Salad Pack', percentage: 55, frequency: 63 },
            { rank: 5, name: 'Organic Blueberries', percentage: 48, frequency: 54 }
        ],
        recentLogs: [
            { orderId: '#FC-29381', product: 'Organic Cherry Tomatoes', source: 'NAVER', status: 'COMPLETED', time: 'Just now' },
            { orderId: '#FC-29380', product: 'Fresh Avocado', source: 'KURLY', status: 'COMPLETED', time: '3 min' },
            { orderId: '#FC-29379', product: 'Premium Mango', source: 'COUPANG', status: 'COMPLETED', time: '12 min' },
            { orderId: '#FC-29378', product: 'Mixed Salad Pack', source: 'NAVER', status: 'PENDING', time: '25 min' }
        ]
    };
    
    updateStats(mockData);
    updateKeywords(mockData.keywords);
    updateWordCloud(mockData.keywords);
    updateRecentLogs(mockData.recentLogs);
}

function updateStats(data) {
    document.getElementById('totalProducts').textContent = data.totalProducts || 0;
    document.getElementById('totalMembers').textContent = data.totalMembers || 0;
    document.getElementById('totalSales').textContent = data.totalSales || 0;
    
    // Ensure totalRevenue is a number before formatting
    const revenue = data.totalRevenue ? parseFloat(data.totalRevenue) : 0;
    document.getElementById('totalRevenue').textContent = formatPrice(revenue);
}

function updateKeywords(keywords) {
    const keywordsList = document.getElementById('keywordsList');
    if (!keywordsList) return;
    
    keywordsList.innerHTML = keywords.map(keyword => `
        <div class="keyword-item">
            <span class="kw-rank" style="background:${getKeywordColor(keyword.rank)}">${keyword.rank}</span>
            <span class="kw-name">${keyword.name}</span>
            <div class="kw-bar-wrap">
                <div class="kw-bar" style="width:${keyword.percentage}%"></div>
            </div>
            <span class="kw-pct">${keyword.percentage}%</span>
        </div>
    `).join('');
}

function updateWordCloud(keywords) {
    const wordCloud = document.getElementById('wordCloud');
    if (!wordCloud) return;
    
    // Randomize display order so high-frequency words are not grouped by rank.
    const shuffledKeywords = [...keywords];
    for (let i = shuffledKeywords.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [shuffledKeywords[i], shuffledKeywords[j]] = [shuffledKeywords[j], shuffledKeywords[i]];
    }
    
    // Calculate font size based on frequency range
    const frequencies = keywords.map(k => k.frequency);
    const maxFreq = Math.max(...frequencies);
    const minFreq = Math.min(...frequencies);
    const freqRange = maxFreq - minFreq || 1;
    
    wordCloud.innerHTML = shuffledKeywords.map((keyword, index) => {
        // Normalize frequency to font size range (16px to 36px)
        const normalizedFreq = (keyword.frequency - minFreq) / freqRange;
        const fontSize = 16 + normalizedFreq * 20; // 16px to 36px range
        
        const fontWeight = normalizedFreq > 0.7 ? '800' : normalizedFreq > 0.4 ? '700' : '500';
        const color = normalizedFreq > 0.7 ? '#1a7a4a' : normalizedFreq > 0.4 ? '#2d6a4f' : '#999';
        
        // Natural organic positioning - no fixed positions
        let positionStyle = 'position:relative;';
        
        // Add slight animation delay for staggered appearance
        const animationDelay = index * 40;
        
        return `<span style="font-size:${fontSize}px;font-weight:${fontWeight};color:${color};cursor:default;animation:fadeInUp 0.3s ease-out ${animationDelay}ms both;order:${index};${positionStyle}">${keyword.name}</span>`;
    }).join('');
}

function updateRecentLogs(logs) {
    const recentLogs = document.getElementById('recentLogs');
    if (!recentLogs) return;

    // Sample logs if no data
    const sampleLogs = logs.length > 0 ? logs : [
        { orderId: '#FC-29381', product: 'Organic Cherry Tomatoes', source: 'NAVER', status: 'COMPLETED', time: 'Just now' },
        { orderId: '#FC-29380', product: 'Fresh Avocado', source: 'KURLY', status: 'COMPLETED', time: '3 min' },
        { orderId: '#FC-29379', product: 'Premium Mango', source: 'COUPANG', status: 'COMPLETED', time: '12 min' },
        { orderId: '#FC-29378', product: 'Mixed Salad Pack', source: 'NAVER', status: 'PENDING', time: '25 min' }
    ];

    recentLogs.innerHTML = sampleLogs.map(log => `
        <tr>
            <td style="color:#2d9a5e;font-weight:600">${log.orderId}</td>
            <td>${log.product}</td>
            <td><span class="src ${getSourceClass(log.source)}">${log.source}</span></td>
            <td><span class="dot ${getStatusDot(log.status)}"></span><span class="${getStatusClass(log.status)}">${log.status}</span></td>
            <td style="color:#ccc">${log.time}</td>
        </tr>
    `).join('');
}

function updateRpaExtractionStatus(rpaData) {
    // Update the RPA Data Extraction Status panel
    // The chart will be updated dynamically based on real data
    console.log('RPA Extraction Data:', rpaData);
    
    // You can add more dynamic chart updates here if needed
    // For now, the data is available for future enhancements
    
    // Update panel subtitle with last extraction info
    const panelSub = document.querySelector('.panel:nth-child(2) .panel-sub');
    if (panelSub && rpaData.length > 0) {
        const totalExtractions = rpaData.reduce((sum, item) => sum + item.extractionCount, 0);
        const successRate = ((rpaData.reduce((sum, item) => sum + item.successCount, 0) / totalExtractions) * 100).toFixed(1);
        panelSub.textContent = `Past 24 hours: ${totalExtractions} extractions, ${successRate}% success rate`;
    }
}

function getKeywordColor(rank) {
    const colors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7', '#DDA0DD'];
    return colors[rank % colors.length];
}

function getSourceClass(source) {
    const classes = {
        'NAVER': 'sn',
        'KURLY': 'sk',
        'COUPANG': 'sc'
    };
    return classes[source] || 'sn';
}

function getStatusDot(status) {
    const dots = {
        'COMPLETED': 'dg',
        'PENDING': 'dw',
        'FAILED': 'dr'
    };
    return dots[status] || 'dw';
}

function getStatusClass(status) {
    const classes = {
        'COMPLETED': 'sd',
        'PENDING': 'sw',
        'FAILED': 'sw'
    };
    return classes[status] || 'sw';
}

function viewAllKeywords() {
    showToast('View all keywords feature coming soon...');
}

function handleLogout() {
    fetch('/api/auth/logout', { method: 'POST' })
        .then(() => {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('role');
            window.location.href = '/main.html';
        })
        .catch(() => {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('role');
            window.location.href = '/main.html';
        });
}

// Auto-refresh dashboard every 30 seconds
setInterval(() => {
    if (document.getElementById('totalProducts')) {
        loadDashboardData();
    }
}, 30000);

// Orders functions
function createOrderItem(order) {
    const statusClass = getStatusClass(order.status);
    const statusText = getStatusText(order.status);

    return `
        <div class="order-item" onclick="selectOrder(${order.id})" data-order-id="${order.id}">
            <div class="order-meta">
                <span class="order-id">#${order.orderNumber}</span>
                <span class="sp ${statusClass}">${statusText}</span>
            </div>
            <div class="order-name">${order.customerName} (${order.items.length} items)</div>
            <div class="order-foot">
                <span class="order-date">${formatDate(order.orderDate)}</span>
                <span class="order-price" style="color:#2d9a5e">${formatPrice(order.subtotal + order.shippingFee)}</span>
            </div>
        </div>
    `;
}

function selectOrder(orderId, scrollToDetails = true) {
    selectedOrderId = orderId;

    // Update selection UI
    document.querySelectorAll('.order-item').forEach(item => {
        item.classList.remove('sel');
    });
    document.querySelector(`[data-order-id="${orderId}"]`).classList.add('sel');

    // Load order details
    fetch(`/api/admin/orders/${orderId}`)
        .then(response => response.json())
        .then(order => {
            console.log('=== JavaScript DEBUG: Received Order Data ===');
            console.log('Order ID:', order.id);
            console.log('Order Items Count:', order.items ? order.items.length : 0);
            
            if (order.items && order.items.length > 0) {
                order.items.forEach((item, index) => {
                    console.log(`Item ${index + 1}:`);
                    console.log('  - productName:', item.productName);
                    console.log('  - quantity:', item.quantity);
                    console.log('  - unitPrice:', item.unitPrice);
                    console.log('  - totalPrice:', item.totalPrice);
                    console.log('  - typeof unitPrice:', typeof item.unitPrice);
                    console.log('  - typeof totalPrice:', typeof item.totalPrice);
                });
            }
            
            console.log('Order subtotal:', order.subtotal);
            console.log('Order shippingFee:', order.shippingFee);
            console.log('Order totalPrice:', order.totalPrice);
            console.log('===========================================');
            
            displayOrderDetails(order, scrollToDetails);
        })
        .catch(error => {
            console.error('Error loading order details:', error);
            showToast('Failed to load order details');
        });
}

function displayOrderDetails(order, scrollToDetails = true) {
    const invoiceArea = document.getElementById('invoiceArea');

    const statusSteps = getStatusSteps(order.status);

    invoiceArea.innerHTML = `
        <div class="invoice-card">
            <div class="inv-header-bar">
                <div>
                    <div class="inv-brand">DaDream</div>
                    <div class="inv-addr">DaDream RPA Mall<br>T. 031-719-3688 | E. hello@dadream.com</div>
                </div>
                <div class="inv-title-right">
                    <div class="inv-title">Invoice</div>
                    <div class="inv-num">#${order.orderNumber}</div>
                    <div class="inv-date">${formatDate(order.orderDate)}</div>
                </div>
            </div>
            <div class="inv-status-bar">
                ${statusSteps.map(step => `<div class="inv-status-step ${step.class}"></div>`).join('')}
            </div>
            <div class="invoice-meta-grid">
                <div class="inv-section">
                    <div class="inv-lbl">BILL TO</div>
                    <div style="font-weight:700;font-size:14px;color:#1a2e22">${order.customerName}</div>
                    <div style="font-size:11.5px;color:#999;margin-top:4px;line-height:1.7">
                        ${order.shippingAddress}<br>
                        ${order.customerPhone}
                    </div>
                    <div style="color:${getStatusColor(order.status)};font-size:11.5px;margin-top:6px;font-weight:600">
                        ${getStatusIcon(order.status)} ${getStatusText(order.status)}
                    </div>
                </div>
                <div class="inv-section">
                    <div class="inv-lbl">PAYMENT INFO</div>
                    <div style="font-weight:700;font-size:14px;color:#1a2e22">${order.customerName}</div>
                    <div style="font-size:11.5px;color:#999;margin-top:4px;line-height:1.7">
                        ${order.paymentMethod}<br>
                        ${order.paymentStatus}
                    </div>
                    <div style="font-size:11px;color:${getStatusColor(order.status)};margin-top:4px;font-weight:600">
                        ${getStatusText(order.status)} 
                    </div>
                </div>
            </div>
            <div class="invoice-table-wrap">
                <table class="inv-table">
                    <tr>
                        <th style="width:50%">DESCRIPTION</th>
                        <th>QTY</th>
                        <th>UNIT PRICE</th>
                        <th>AMOUNT</th>
                    </tr>
                    ${order.items.map(item => `
                        <tr>
                            <td>
                                <div style="font-weight:600;color:#1a2e22">${item.productName}</div>
                                <div style="font-size:10px;color:#ccc">${item.description || ''}</div>
                            </td>
                            <td style="color:#666">${item.quantity}</td>
                            <td style="color:#666">${formatPrice(item.unitPrice)}</td>
                            <td><strong>${formatPrice(item.totalPrice)}</strong></td>
                        </tr>
                    `).join('')}
                </table>
            </div>
            <div class="invoice-total-summary">
                <div class="invoice-total-row">
                    <span>Subtotal</span>
                    <span>${formatPrice(order.subtotal)}</span>
                </div>
                <div class="invoice-total-row">
                    <span>Shipping</span>
                    <span style="color:#2d9a5e">${order.shippingFee > 0 ? formatPrice(order.shippingFee) : 'FREE'}</span>
                </div>
                <div class="invoice-total-row invoice-total-row-final">
                    <span style="color:#1a2e22">TOTAL</span>
                    <span class="total-pill">${formatPrice(order.subtotal + order.shippingFee)}</span>
                </div>
            </div>
            <div style="margin-top:20px;padding-top:14px;border-top:1px solid #f0f2f0;text-align:center;font-size:9.5px;color:#ccc">
                © 2026 DaDream RPA MALL. ALL RIGHTS RESERVED.
            </div>
        </div>
    `;
}

// Helper functions for orders
function getStatusClass(status) {
    const classes = {
        'PROCESSING': 'pp',
        'QUEUED': 'pq',
        'FAILED': 'pf',
        'COMPLETED': 'pp',
        'CANCELLED': 'pf'
    };
    return classes[status] || 'pq';
}

function getStatusText(status) {
    const texts = {
        'PROCESSING': 'Processing',
        'QUEUED': 'Queued',
        'FAILED': 'Failed',
        'COMPLETED': 'Completed',
        'CANCELLED': 'Cancelled'
    };
    return texts[status] || 'Unknown';
}

function getStatusColor(status) {
    const colors = {
        'PROCESSING': '#1a7a4a',
        'QUEUED': '#92400e',
        'FAILED': '#991b1b',
        'COMPLETED': '#1a7a4a',
        'CANCELLED': '#991b1b'
    };
    return colors[status] || '#666';
}

function getStatusIcon(status) {
    const icons = {
        'PROCESSING': 'Processing',
        'QUEUED': 'Queued',
        'FAILED': 'Failed',
        'COMPLETED': 'Completed',
        'CANCELLED': 'Cancelled'
    };
    return icons[status] || '';
}

function getStatusSteps(status) {
    const steps = [
        { class: 'done' },
        { class: 'done' },
        { class: 'active' },
        { class: '' }
    ];

    // Adjust based on status
    if (status === 'QUEUED') {
        steps[1].class = 'active';
        steps[2].class = '';
    } else if (status === 'FAILED' || status === 'CANCELLED') {
        steps[0].class = 'done';
        steps[1].class = '';
        steps[2].class = '';
        steps[3].class = '';
    }

    return steps;
}

function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR') + ' ' + date.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' });
}


function isToday(dateString) {
    const date = new Date(dateString);
    const today = new Date();
    return date.toDateString() === today.toDateString();
}

function exportOrders() {
    // Get current search term
    const searchTerm = document.getElementById('orderSearch').value.toLowerCase();
    
    // Get visible orders (filtered by search)
    const visibleOrderItems = document.querySelectorAll('.order-item:not([style*="display: none"])');
    const orderIds = Array.from(visibleOrderItems).map(item => 
        item.getAttribute('data-order-id')
    );
    
    console.log('Exporting filtered orders:', orderIds.length, 'orders found');
    console.log('Search term:', searchTerm);
    console.log('Order IDs to export:', orderIds);
    
    // If no search term, export all orders
    if (!searchTerm) {
        fetch('/api/admin/orders/export')
        .then(response => {
            console.log('Export response status:', response.status);
            if (!response.ok) {
                throw new Error('Failed to export orders: ' + response.status);
            }
            return response.blob();
        })
        .then(blob => {
            // Create download link
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = url;
            a.download = 'orders_export_' + new Date().toISOString().split('T')[0] + '.xlsx';
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
            
            showToast('All orders exported successfully');
        })
        .catch(error => {
            console.error('Error exporting orders:', error);
            showToast('Failed to export orders: ' + error.message);
        });
    } else {
        // Export filtered orders
        fetch('/api/admin/orders/export/filtered', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                orderIds: orderIds,
                searchTerm: searchTerm
            })
        })
        .then(response => {
            console.log('Export response status:', response.status);
            if (!response.ok) {
                throw new Error('Failed to export orders: ' + response.status);
            }
            return response.blob();
        })
        .then(blob => {
            // Create download link
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.href = url;
            a.download = 'orders_filtered_' + searchTerm + '_' + new Date().toISOString().split('T')[0] + '.xlsx';
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
            
            showToast('Filtered orders exported successfully');
        })
        .catch(error => {
            console.error('Error exporting orders:', error);
            showToast('Failed to export orders: ' + error.message);
        });
    }
}

// Members functions
let currentMemberId = null;

function loadMembers() {
    console.log('Loading members...');
    fetch('/api/admin/members/list')
        .then(response => {
            console.log('Members API response status:', response.status);
            if (!response.ok) {
                throw new Error('Failed to load members: ' + response.status);
            }
            return response.json();
        })
        .then(members => {
            console.log('Members loaded:', members);
            const grid = document.getElementById('memberGrid');
            if (!grid) {
                console.error('memberGrid element not found');
                return;
            }
            grid.innerHTML = members.map(member => createMemberCard(member)).join('');
            console.log('Member cards rendered');
        })
        .catch(error => {
            console.error('Error loading members:', error);
            showToast('Failed to load members');
            
            // Load mock data if API fails
            const mockMembers = [
                { id: 1, name: 'Admin User', email: 'admin@dadream.com', role: 'ADMIN', lastLogin: '2026-04-22', createdAt: '2026-01-01' },
                { id: 2, name: 'Test User', email: 'test@dadream.com', role: 'USER', lastLogin: '2026-04-21', createdAt: '2026-02-01' },
                { id: 3, name: 'Demo User', email: 'demo@dadream.com', role: 'USER', lastLogin: '2026-04-20', createdAt: '2026-03-01' }
            ];
            const grid = document.getElementById('memberGrid');
            if (grid) {
                grid.innerHTML = mockMembers.map(member => createMemberCard(member)).join('');
                console.log('Mock members loaded');
            }
        });
}

function createMemberCard(member) {
    const roleClass = member.role === 'ADMIN' ? 'role-admin' : 'role-user';
    const roleText = member.role === 'ADMIN' ? 'Admin' : 'User';
    const avatarEmoji = member.role === 'ADMIN' ? 'A' : 'U';
    const memberName = JSON.stringify(member.name ?? '');
    const memberRole = JSON.stringify(member.role ?? 'USER');

    return `
        <div class="member-card">
            <div class="member-av-wrap">
                <div class="member-av" style="background:#${member.role === 'ADMIN' ? 'edf7f2' : 'f0f0ec'};font-size:26px">${avatarEmoji}</div>
                <div class="member-av-status" style="background:#2d9a5e"></div>
            </div>
            <div class="member-info">
                <span class="member-role-badge ${roleClass}">${roleText}</span>
                <div class="member-name">${member.name}</div>
                <div class="member-email">${member.email}</div>
                <div class="member-meta">Last login: ${member.lastLogin || 'Unknown'} · Joined: ${member.createdAt || 'Unknown'}</div>
            </div>
            <div class="member-actions">
                <button class="m-btn m-role" onclick='showRoleModal(${member.id}, ${memberName}, ${memberRole})'>
                    <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M8 1l2 4 4.5.7-3.3 3.2.8 4.5L8 11.3 4 13.4l.8-4.5L1.5 5.7 6 5z"/></svg>Role
                </button>
                <button class="m-btn m-del" onclick='showDeleteModal(${member.id}, ${memberName})'>
                    <svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M2 4h12M5 4V2h6v2M6 7v5M10 7v5M3 4l1 9h8l1-9"/></svg>Delete
                </button>
            </div>
        </div>
    `;
}

// Modal functions
function showModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.add('show');
    }
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (modal) {
        modal.classList.remove('show');
    }
}

function hideModal(id) {
    closeModal(id);
}

// Delete member
function showDeleteModal(id, name) {
    console.log('Show delete modal for member:', id, name);
    currentMemberId = id;
    const delNameElement = document.getElementById('del-name');
    if (delNameElement) {
        delNameElement.textContent = name;
        showModal('modal-delete');
    } else {
        console.error('del-name element not found');
    }
}

function confirmDelete() {
    if (!currentMemberId) return;

    fetch(`/api/admin/members/${currentMemberId}`, {
        method: 'DELETE'
    })
        .then(response => {
            if (response.ok) {
                closeModal('modal-delete');
                showToast('회원이 삭제되었습니다.');
                loadMembers();
            } else {
                return response.json().then(errorData => {
                    const message = errorData.message || 'Failed to delete member';
                    if (message.includes('관리자 계정은 삭제할 수 없습니다') || message.includes('Admin accounts cannot be deleted')) {
                        showToast('Admin accounts cannot be deleted');
                    } else {
                        showToast(message);
                    }
                }).catch(() => {
                    // Fallback if response is not JSON
                    return response.text().then(text => {
                        if (text.includes('관리자 계정은 삭제할 수 없습니다')) {
                            showToast('Admin accounts cannot be deleted');
                        } else {
                            showToast('Failed to delete member');
                        }
                    });
                });
            }
        })
        .catch(error => {
            console.error('Error:', error);
            if (error.message.includes('관리자 계정은 삭제할 수 없습니다')) {
                showToast('Admin accounts cannot be deleted');
            } else {
                showToast('Error occurred');
            }
        })
        .finally(() => {
            currentMemberId = null;
        });
}

function confirmDelete() {
    if (!currentMemberId) return;

    fetch(`/api/admin/members/${currentMemberId}`, {
        method: 'DELETE'
    })
        .then(response => {
            if (response.ok) {
                closeModal('modal-delete');
                showToast('회원이 삭제되었습니다.');
                loadMembers();
                return null;
            }

            return response.text().then(text => {
                let message = '회원 삭제에 실패했습니다.';

                try {
                    const errorData = JSON.parse(text);
                    if (errorData && errorData.message) {
                        message = errorData.message;
                    }
                } catch (_) {
                    if (text && text.trim()) {
                        message = text.trim();
                    }
                }

                if (message.includes('관리자 계정은 삭제할 수 없습니다')) {
                    showToast('관리자 계정은 삭제할 수 없습니다.');
                } else {
                    showToast(message);
                }

                return null;
            });
        })
        .catch(error => {
            console.error('Error:', error);
            showToast('회원 삭제 중 오류가 발생했습니다.');
        })
        .finally(() => {
            currentMemberId = null;
        });
}

// Role management
function showRoleModal(id, name, currentRole) {
    console.log('Show role modal for member:', id, name, currentRole);
    currentMemberId = id;
    const roleNameElement = document.getElementById('role-name');
    const select = document.getElementById('role-select');
    
    if (roleNameElement && select) {
        roleNameElement.textContent = name;
        select.value = currentRole;
        showModal('modal-role');
    } else {
        console.error('role-name or role-select element not found');
    }
}

function confirmRole() {
    if (!currentMemberId) return;

    const newRole = document.getElementById('role-select').value;

    fetch(`/api/admin/members/${currentMemberId}/role`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({ role: newRole })
    })
        .then(response => {
            if (response.ok) {
                closeModal('modal-role');
                showToast('Role updated successfully');
                loadMembers(); // Reload members
            } else {
                showToast('Failed to update role');
            }
        })
        .catch(error => {
            console.error('Error:', error);
            showToast('Error occurred while executing auto order');
        });
}

// Products functions
let currentProductId = null;
let currentProductName = null;
let currentAvailableQty = null;

let currentPage = 0;
const pageSize = 10;
let isLoading = false;
let hasMore = true;
let currentSearchTerm = null;

function loadProducts() {
    console.log('Loading products...');
    loadProductsPage();
    // Adjust layout after products are loaded
    setTimeout(adjustLayoutForScreenSize, 100);
}

function loadProductsPage(page = 0) {
    isLoading = true;
    const loadingIndicator = document.getElementById('loadingIndicator');
    if (loadingIndicator) {
        loadingIndicator.style.display = 'block';
    }
    
    // Add timeout controller
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000); // 10초 타임아웃
    
    fetch(`/api/admin/products/list/paged?page=${page}&size=${pageSize}`, {
        signal: controller.signal
    })
        .then(response => {
            clearTimeout(timeoutId);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return response.json();
        })
        .then(products => {
            const grid = document.getElementById('productGrid');
            if (grid) {
                // Use innerHTML for HTML strings instead of appendChild
                grid.innerHTML = products.map(product => createProductCard(product)).join('');
                console.log('Product cards rendered');
            }
            
            // Apply dynamic sizing to newly created cards
            setTimeout(adjustLayoutForScreenSize, 100);
            
            // Update pagination info
            currentPage = page;
            hasMore = products.length === pageSize;
            
            // Render pagination controls
            updatePaginationControls(page, products.length);
        })
        .catch(error => {
            clearTimeout(timeoutId);
            if (error.name === 'AbortError') {
                console.error('Request timeout:', error);
                showToast('요청 시간이 초과되었습니다. 다시 시도해주세요.');
            } else {
                console.error('Error loading products:', error);
                showToast('상품을 불러오는 데 실패했습니다. 다시 시도해주세요.');
            }
            
            const grid = document.getElementById('productGrid');
            if (grid) {
                grid.innerHTML = '<div style="text-align: center; padding: 40px; color: #666;">상품을 불러오는 데 실패했습니다. 다시 시도해주세요.</div>';
            }
        })
        .finally(() => {
            isLoading = false;
            const loadingIndicator = document.getElementById('loadingIndicator');
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
        });
}

function searchProductsFromDB(searchTerm, page = 0) {
    if (!searchTerm) {
        // If search term is empty, load default products
        loadProductsPage(0);
        return;
    }

    isLoading = true;
    const loadingIndicator = document.getElementById('loadingIndicator');
    if (loadingIndicator) {
        loadingIndicator.style.display = 'block';
    }

    // Store current search term for pagination
    currentSearchTerm = searchTerm;

    // Add timeout controller
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000); // 10초 타임아웃
    
    fetch(`/api/admin/products/search?term=${encodeURIComponent(searchTerm)}&page=${page}&size=${pageSize}`, {
        signal: controller.signal
    })
        .then(response => {
            clearTimeout(timeoutId);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return response.json();
        })
        .then(products => {
            const grid = document.getElementById('productGrid');
            if (grid) {
                grid.innerHTML = products.map(product => createProductCard(product)).join('');
            }
            
            // Apply dynamic sizing to newly created cards
            setTimeout(adjustLayoutForScreenSize, 100);
            
            // Update pagination for search results
            updatePaginationControls(page, products.length);
            
            // Show search result count only for first page
            if (page === 0) {
                showToast(`${products.length}개의 제품을 찾았습니다.`);
            }
            
            isLoading = false;
        })
        .catch(error => {
            clearTimeout(timeoutId);
            if (error.name === 'AbortError') {
                console.error('Search request timeout:', error);
                showToast('검색 요청 시간이 초과되었습니다. 다시 시도해주세요.');
            } else {
                console.error('Error searching products:', error);
                showToast('검색에 실패했습니다. 다시 시도해주세요.');
            }
            
            const grid = document.getElementById('productGrid');
            if (grid) {
                grid.innerHTML = '<div style="text-align: center; padding: 40px; color: #666;">검색 결과가 없거나 오류가 발생했습니다.</div>';
            }
            
            isLoading = false;
        });
}


function updatePaginationControls(page, productCount) {
    // Check if we have more products
    hasMore = productCount === pageSize;
    
    const pagination = document.getElementById('paginationControls');
    if (pagination) {
        pagination.innerHTML = `
            <div class="pagination-left">
                <div class="pagination-arrow ${page === 0 ? 'disabled' : ''}" ${page > 0 ? `onclick="goToPage(${page - 1})"` : ''}>
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="${page > 0 ? '#2d9a5e' : '#ccc'}" stroke-width="2">
                        <path d="M15 18l-6-6 6-6"/>
                    </svg>
                </div>
            </div>
            <div class="pagination-info">Page ${page + 1}</div>
            <div class="pagination-right">
                <div class="pagination-arrow ${hasMore ? '' : 'disabled'}" ${hasMore ? `onclick="goToPage(${page + 1})"` : ''}>
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="${hasMore ? '#2d9a5e' : '#ccc'}" stroke-width="2">
                        <path d="M9 18l6-6-6-6"/>
                    </svg>
                </div>
            </div>
        `;
    }
}

// Add window resize handler for dynamic adjustment
window.addEventListener('resize', function() {
    adjustLayoutForScreenSize();
});

function adjustLayoutForScreenSize() {
    const screenHeight = window.innerHeight;
    const screenWidth = window.innerWidth;
    const grid = document.getElementById('productGrid');
    const pagination = document.getElementById('paginationControls');
    
    if (grid && pagination) {
        let cardPadding = 16;
        let imageSize = 75;
        let fontSize = 12;
        let infoBoxWidth = '130px';
        let nodeBoxWidth = '65px';
        let gap = 20;

        if (screenWidth <= 480) {
            cardPadding = 12;
            imageSize = 56;
            fontSize = 11;
            infoBoxWidth = '100px';
            nodeBoxWidth = '48px';
            gap = 10;
        } else if (screenWidth <= 768) {
            cardPadding = 14;
            imageSize = 64;
            fontSize = 11;
            infoBoxWidth = '112px';
            nodeBoxWidth = '54px';
            gap = 12;
        } else if (screenWidth <= 1200) {
            cardPadding = 14;
            imageSize = 68;
            fontSize = 11;
            infoBoxWidth = '120px';
            nodeBoxWidth = '58px';
            gap = 16;
        }

        grid.style.gap = gap + 'px';
        grid.style.height = 'auto';
        grid.style.maxHeight = 'none';
        grid.style.overflow = 'visible';
        pagination.style.display = 'flex';
         
        // Update CSS variables for responsive design
        document.documentElement.style.setProperty('--card-padding', cardPadding + 'px');
        document.documentElement.style.setProperty('--image-size', imageSize + 'px');
        document.documentElement.style.setProperty('--font-size', fontSize + 'px');
        document.documentElement.style.setProperty('--info-box-width', infoBoxWidth);
        document.documentElement.style.setProperty('--node-box-width', nodeBoxWidth);
        
        // Set responsive image grid columns based on screen width
        let imageColumns, imageGap;
        if (screenWidth < 600) {
            imageColumns = 2;
            imageGap = 4;
        } else if (screenWidth < 900) {
            imageColumns = 3;
            imageGap = 5;
        } else if (screenWidth < 1200) {
            imageColumns = 4;
            imageGap = 6;
        } else {
            imageColumns = 5;
            imageGap = 8;
        }
        
        document.documentElement.style.setProperty('--image-columns', imageColumns);
        document.documentElement.style.setProperty('--image-gap', imageGap + 'px');
    }
}

function goToPage(page) {
    currentPage = page;
    
    // If we have an active search, continue searching
    if (currentSearchTerm) {
        searchProductsFromDB(currentSearchTerm, page);
    } else {
        loadProductsPage(page);
    }
}

function createProductCard(product) {
    const emoji = getProductEmoji(product.name);
    const tag = getProductTag(product.name);
    const stockStatus = product.stock < 10 ? 'Low Stock' : 'In Stock';
    const stockColor = product.stock < 10 ? '#c0392b' : '#2d6a4f';
    const description = product.description && product.description.trim()
        ? product.description
        : '상품 설명이 없습니다.';

    return `
        <div class="product-card">
            <div class="product-poster">
                ${product.imageUrl ? 
                    `<img src="${product.imageUrl}" alt="${product.name}" onerror="this.style.display='none'; this.parentElement.innerHTML='${emoji}';">` : 
                    `<span>이미지 로드 실패</span>`
                }
                <div class="product-synopsis">
                    <div class="product-synopsis-inner">
                        <div class="product-description">
                            ${description}
                        </div>
                        <div class="product-overlay-details">
                            <div class="product-overlay-row">
                                <span class="detail-label">가격</span>
                                <span class="detail-value price">${formatPrice(product.price)}</span>
                            </div>
                            <div class="product-overlay-row">
                                <span class="detail-label">재고</span>
                                <span class="detail-value stock" style="color: ${stockColor}">${product.stock}개</span>
                            </div>
                            <div class="product-overlay-row">
                                <span class="detail-label">발주가능</span>
                                <span class="detail-value available">${product.stock * 3}개</span>
                            </div>
                            <div class="product-overlay-row">
                                <span class="detail-label">상태</span>
                                <span class="detail-value status">${stockStatus}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <div class="product-info">
                <h3 class="product-title">${product.name}</h3>
                <div class="product-details">
                    <div class="detail-row">
                        <span class="detail-label">가격</span>
                        <span class="detail-value price">${formatPrice(product.price)}</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">재고</span>
                        <span class="detail-value stock" style="color: ${stockColor}">${product.stock}개</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">발주가능</span>
                        <span class="detail-value available">${product.stock * 3}개</span>
                    </div>
                    <div class="detail-row">
                        <span class="detail-label">상태</span>
                        <span class="detail-value status">${stockStatus}</span>
                    </div>
                </div>
                <div class="product-actions">
                    <button class="auto-order-btn ${product.autoOrder ? 'active' : ''}" onclick="toggleAutoOrder(${product.id}, ${product.autoOrder})">
                        ${product.autoOrder ? 'Auto Ordering' : 'Auto Order'}
                    </button>
                </div>
            </div>
        </div>
    `;
}

function getProductEmoji(productName) {
    if (productName.toLowerCase().includes('tomato')) return 'Tomato';
    if (productName.toLowerCase().includes('kale')) return 'Kale';
    if (productName.toLowerCase().includes('avocado')) return 'Avocado';
    if (productName.toLowerCase().includes('strawberry')) return 'Strawberry';
    if (productName.toLowerCase().includes('salad')) return 'Salad';
    return 'Product';
}

function getProductTag(productName) {
    if (productName.toLowerCase().includes('organic')) return 'ORGANIC';
    if (productName.toLowerCase().includes('fresh')) return 'FRESH';
    if (productName.toLowerCase().includes('premium')) return 'PREMIUM';
    if (productName.toLowerCase().includes('mixed')) return 'MIXED';
    return 'SEASONAL';
}

// Auto order functions
let currentAutoOrderProductId = null;

function toggleAutoOrder(productId, currentStatus) {
    currentAutoOrderProductId = productId;
    currentAutoOrderStatus = currentStatus; // Store current status
    const modal = document.getElementById('modal-auto-order');
    const reasonTextarea = document.getElementById('auto-order-reason');
    
    if (modal && reasonTextarea) {
        reasonTextarea.value = currentStatus ? 'Auto order disabled by admin' : 'Auto order enabled by admin';
        showModal('modal-auto-order');
    }
}

function confirmAutoOrder() {
    if (!currentAutoOrderProductId) return;
    
    const reasonTextarea = document.getElementById('auto-order-reason');
    const reason = reasonTextarea.value;
    
    // Toggle the current status
    const newAutoOrderStatus = !currentAutoOrderStatus;
    
    fetch(`/api/admin/products/${currentAutoOrderProductId}/auto-order`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            autoOrder: newAutoOrderStatus,
            reason: reason
        })
    })
    .then(response => {
        if (response.ok) {
            const message = newAutoOrderStatus ? 'Auto order enabled.' : 'Auto order disabled.';
            showToast(message);
            hideModal('modal-auto-order');
            loadProductsPage(currentPage); // Reload current page only
        } else {
            showToast('Failed to execute auto order');
        }
    })
    .catch(error => {
        console.error('Error:', error);
        showToast('Error occurred while executing auto order');
    })
    .finally(() => {
        currentAutoOrderProductId = null;
        currentAutoOrderStatus = null;
    });
}

    // Orders functions
    let currentOrderId = null;
    let currentOrderNumber = null;
    let currentCustomerName = null;

    // Pagination variables for orders
    let currentOrderPage = 0;
    const orderPageSize = 10;
    let hasMoreOrders = true;
    let isLoadingOrders = false;

    function loadOrders() {
        currentOrderPage = 0;
        hasMoreOrders = true;

        const orderItems = document.getElementById('orderItems');
        if (orderItems) {
            orderItems.innerHTML = '';
        }

        loadOrdersPage(true);
    }

    function updateOrderCounts(orders) {
        const activeCount = orders.filter(o => o.status === 'PROCESSING').length;
        const todayCount = orders.filter(o => isToday(o.orderDate)).length;
        
        document.getElementById('activeCount').textContent = activeCount;
        document.getElementById('todayCount').textContent = todayCount;
    }

    function updateOrderPaginationControls() {
        let paginationContainer = document.getElementById('orderPaginationControls');
        if (!paginationContainer) {
            const orderItems = document.getElementById('orderItems');
            paginationContainer = document.createElement('div');
            paginationContainer.id = 'orderPaginationControls';
            paginationContainer.className = 'pagination-container';
            orderItems.parentNode.insertBefore(paginationContainer, orderItems.nextSibling);
        }
        
        if (hasMoreOrders) {
            paginationContainer.innerHTML = `
                <button class="load-more-btn" onclick="loadOrdersPage()" ${isLoadingOrders ? 'disabled' : ''}>
                    ${isLoadingOrders ? '로딩 중...' : '더 보기'}
                </button>
            `;
        } else {
            paginationContainer.innerHTML = `
                <div class="no-more-orders">모든 주문을 불러왔습니다.</div>
            `;
        }
    }

    function loadOrdersPage(reset = false) {
        if (isLoadingOrders) {
            return;
        }

        isLoadingOrders = true;
        updateOrderPaginationControls();

        fetch(`/api/admin/orders/list/paged?page=${currentOrderPage}&size=${orderPageSize}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }
                return response.json();
            })
            .then(data => {
                const orderItems = document.getElementById('orderItems');
                if (!orderItems) {
                    return;
                }

                const orders = data.orders || [];

                if (reset) {
                    orderItems.innerHTML = '';
                }

                orderItems.insertAdjacentHTML('beforeend', orders.map(order => createOrderItem(order)).join(''));
                hasMoreOrders = Boolean(data.hasMore);

                if (orders.length > 0) {
                    currentOrderPage += 1;
                }

                document.getElementById('activeCount').textContent = data.activeCount ?? 0;
                document.getElementById('todayCount').textContent = data.todayCount ?? 0;

            })
            .catch(error => {
                console.error('Error loading orders:', error);
                showToast('Failed to load orders');
            })
            .finally(() => {
                isLoadingOrders = false;
                updateOrderPaginationControls();
            });
    }

    function displayOrderDetails(order) {
        const invoiceArea = document.getElementById('invoiceArea');
        
        invoiceArea.innerHTML = `
            <div class="invoice-card">
                <div class="inv-header-bar">
                    <div>
                        <div class="inv-brand">DaDream</div>
                        <div class="inv-addr">123 Food Street, Seoul, Korea</div>
                    </div>
                    <div class="inv-title-right">
                        <div class="inv-title">INVOICE</div>
                        <div class="inv-num">#${order.orderNumber}</div>
                        <div class="inv-date">${formatDate(order.orderedAt)}</div>
                    </div>
                </div>
            
            <div class="inv-section">
                <div class="inv-lbl">Bill To</div>
                <div style="font-weight:600;color:#1a2e22;margin-bottom:4px">${order.customerName}</div>
                <div style="font-size:12px;color:#666">${order.customerEmail}</div>
                <div style="font-size:12px;color:#666">${order.customerPhone}</div>
                <div style="font-size:12px;color:#666;margin-top:4px">${order.shippingAddress}</div>
            </div>
            
            <div class="inv-section">
                <div class="inv-lbl">Order Items</div>
                <table class="inv-table">
                    <thead>
                        <tr>
                            <th>Item</th>
                            <th>Qty</th>
                            <th>Price</th>
                            <th>Total</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${order.items.map(item => `
                            <tr>
                                <td>${item.productName}</td>
                                <td>${item.quantity}</td>
                                <td>${formatPrice(item.unitPrice)}</td>
                                <td>${formatPrice(item.totalPrice)}</td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
            
            <div class="inv-section">
                <div class="inv-lbl">Payment Summary</div>
                <table class="inv-table">
                    <tr>
                        <td>Subtotal</td>
                        <td>${formatPrice(order.subtotal)}</td>
                    </tr>
                    <tr>
                        <td>Shipping Fee</td>
                        <td>${formatPrice(order.shippingFee)}</td>
                    </tr>
                    <tr>
                        <td><strong>Total</strong></td>
                        <td><span class="total-pill">${formatPrice(order.subtotal + order.shippingFee)}</span></td>
                    </tr>
                </table>
            </div>
            
            <div class="inv-section">
                <div class="inv-lbl">Payment Information</div>
                <div style="display:flex;justify-content:space-between;margin-bottom:8px">
                    <span>Payment Method:</span>
                    <span style="font-weight:600">${order.paymentMethod}</span>
                </div>
                <div style="display:flex;justify-content:space-between;margin-bottom:8px">
                    <span>Payment Status:</span>
                    <span class="sp ${getStatusClass(order.paymentStatus)}">${order.paymentStatus}</span>
                </div>
                <div style="display:flex;justify-content:space-between">
                    <span>Order Status:</span>
                    <span class="sp ${getStatusClass(order.status)}">${order.status}</span>
                </div>
            </div>
            </div>
        `;

        if (scrollToDetails && window.innerWidth <= 768) {
            invoiceArea.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

function exportOrders() {
    // Get current search term
    const searchTerm = document.getElementById('orderSearch').value.toLowerCase();
    
    // Get visible orders (filtered by search)
    const visibleOrderItems = document.querySelectorAll('.order-item:not([style*="display: none"])');
    const orderIds = Array.from(visibleOrderItems).map(item => 
        item.getAttribute('data-order-id')
    );
    
    console.log('Exporting visible orders:', orderIds.length, 'orders found');
    console.log('Search term:', searchTerm);
    console.log('Order IDs to export:', orderIds);
    
    // Always export only visible orders
    fetch('/api/admin/orders/export/filtered', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            orderIds: orderIds,
            searchTerm: searchTerm || 'all'
        })
    })
    .then(response => {
        console.log('Export response status:', response.status);
        if (!response.ok) {
            throw new Error('Failed to export orders: ' + response.status);
        }
        return response.blob();
    })
    .then(blob => {
        // Create download link
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.style.display = 'none';
        a.href = url;
        
        // Dynamic filename based on search term
        const filename = searchTerm 
            ? 'orders_filtered_' + searchTerm + '_' + new Date().toISOString().split('T')[0] + '.xlsx'
            : 'orders_visible_' + new Date().toISOString().split('T')[0] + '.xlsx';
        
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        
        showToast('Orders exported successfully (' + orderIds.length + ' orders)');
    })
    .catch(error => {
        console.error('Error exporting orders:', error);
        showToast('Failed to export orders: ' + error.message);
    });
}

function handleLogout() {
    fetch('/api/auth/logout', { method: 'POST' })
        .then(() => {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('role');
            window.location.href = '/main.html';
        })
        .catch(() => {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('role');
            window.location.href = '/main.html';
        });
}

// Utility functions
function getStatusClass(status) {
    switch (status) {
        case 'PROCESSING':
        case 'PAID':
            return 'pp';
        case 'PENDING':
        case 'PENDING_PAYMENT':
            return 'pq';
        case 'CANCELLED':
        case 'FAILED':
            return 'pf';
        default:
            return 'pq';
    }
}

function getStatusText(status) {
    switch (status) {
        case 'PROCESSING':
            return 'Processing';
        case 'PAID':
            return 'Paid';
        case 'PENDING':
            return 'Pending';
        case 'PENDING_PAYMENT':
            return 'Pending Payment';
        case 'CANCELLED':
            return 'Cancelled';
        case 'FAILED':
            return 'Failed';
        default:
            return status;
    }
}

function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('ko-KR', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit'
    });
}

function formatPrice(price) {
    // Handle null, undefined, NaN, or invalid values
    if (price == null || isNaN(price) || price === '') {
        return '₩0';
    }
    
    // Convert to number if it's a string
    const numericPrice = typeof price === 'string' ? parseFloat(price) : price;
    
    if (isNaN(numericPrice) || numericPrice < 0) {
        return '₩0';
    }
    
    return new Intl.NumberFormat('ko-KR', {
        style: 'currency',
        currency: 'KRW'
    }).format(numericPrice);
}

function isToday(dateString) {
    const date = new Date(dateString);
    const today = new Date();
    return date.toDateString() === today.toDateString();
}

function showToast(message) {
    const toast = document.getElementById('toast');
    const toastMsg = document.getElementById('toastMsg');
    
    toastMsg.textContent = message;
    toast.style.display = 'flex';
    
    setTimeout(() => {
        toast.style.display = 'none';
    }, 3000);
}

let toastTimeoutId = null;

function showToast(message) {
    const toast = document.getElementById('toast');
    const toastMsg = document.getElementById('toastMsg');

    if (!toast || !toastMsg) {
        return;
    }

    if (toastTimeoutId) {
        clearTimeout(toastTimeoutId);
    }

    toastMsg.textContent = message;
    toast.style.display = 'flex';
    toast.style.opacity = '1';
    toast.style.transform = 'translateY(0)';

    toastTimeoutId = setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(80px)';
    }, 2600);

    setTimeout(() => {
        toast.style.display = 'none';
    }, 3000);
}

// Zoom functions for Orders page
let currentZoom = 100;

function zoomIn() {
    if (currentZoom < 200) {
        currentZoom += 10;
        applyZoom();
        showToast(`확대: ${currentZoom}%`);
    }
}

function zoomOut() {
    if (currentZoom > 50) {
        currentZoom -= 10;
        applyZoom();
        showToast(`축소: ${currentZoom}%`);
    }
}

function applyZoom() {
    const invoiceCard = document.querySelector('.invoice-card:not(#emptyInvoice)');
    
    if (invoiceCard) {
        // Apply zoom only to the invoice content, not the container or scrollbar
        invoiceCard.style.transform = `scale(${currentZoom / 100})`;
        invoiceCard.style.transformOrigin = 'top left';
        invoiceCard.style.transition = 'transform 0.2s ease';
        invoiceCard.style.width = `${100 / (currentZoom / 100)}%`; // Compensate for scaling
    }
}

// Print function for Orders page - prints only INVOICE screen
function printOrders() {
    // Save current zoom level
    const originalZoom = currentZoom;
    
    // Reset zoom to 100% for printing
    currentZoom = 100;
    const invoiceCard = document.querySelector('.invoice-card:not(#emptyInvoice)');
    if (invoiceCard) {
        invoiceCard.style.transform = 'none';
        invoiceCard.style.width = '100%';
    }
    
    // Create print-specific styles for INVOICE only
    const printStyles = `
        <style data-print="orders">
            @media print {
                body * {
                    visibility: hidden;
                }
                .invoice-area, .invoice-area * {
                    visibility: visible;
                }
                .invoice-area {
                    position: absolute;
                    left: 0;
                    top: 0;
                    width: 100%;
                    height: auto;
                    transform: none !important;
                    background: white !important;
                }
                .sidebar, .topbar, .order-list {
                    display: none !important;
                }
                .invoice-card {
                    page-break-inside: avoid;
                    margin: 0;
                    padding: 20px;
                    background: white !important;
                    border: 2px solid #000 !important;
                    box-shadow: none !important;
                }
                .inv-header, .inv-body, .inv-footer {
                    border: 1px solid #000 !important;
                }
                .inv-row {
                    border-bottom: 1px solid #000 !important;
                }
                .inv-total {
                    border-top: 2px solid #000 !important;
                    font-weight: bold !important;
                }
                .inv-table td {
                    border: 1px solid #000 !important;
                    padding: 8px !important;
                }
                .total-pill {
                    background: #000 !important;
                    color: #fff !important;
                    padding: 4px 8px !important;
                    border-radius: 4px !important;
                }
                .btn-zoom, .btn-p, .btn-s, .order-search input, .emptyInvoice {
                    display: none !important;
                }
                * {
                    -webkit-print-color-adjust: exact !important;
                    print-color-adjust: exact !important;
                    color-adjust: exact !important;
                }
                @page {
                    margin: 1cm;
                    size: A4;
                    orientation: portrait;
                }
            }
        </style>
    `;
    
    // Add print styles to head
    const printStyleElement = document.createElement('div');
    printStyleElement.innerHTML = printStyles;
    document.head.appendChild(printStyleElement.firstElementChild);
    
    // Trigger print dialog
    window.print();
    
    // Remove print styles after printing
    setTimeout(() => {
        const printStyles = document.querySelector('style[data-print="orders"]');
        if (printStyles) {
            printStyles.remove();
        }
        
        // Restore original zoom
        currentZoom = originalZoom;
        applyZoom();
    }, 1000);
    
    showToast('INVOICE 프린터 출력 준비 완료');
}
