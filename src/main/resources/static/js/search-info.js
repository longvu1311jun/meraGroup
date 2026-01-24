// Shared search-info JS for demo.html and searchInfo.html
(function () {
  // Tailwind config
  tailwind.config = {
    theme: {
      extend: {
        colors: {
          brand: { 50: '#f0fdfa', 100: '#ccfbf1', 500: '#14b8a6', 600: '#0d9488', 700: '#0f766e' }
        }
      }
    }
  };

  const phoneInput = document.getElementById('phoneInput');
  const searchBtn = document.getElementById('searchBtn');
  const customerCard = document.getElementById('customerCard');
  const customerInfo = document.getElementById('customerInfo');
  const ordersWrap = document.getElementById('ordersWrap') || document.getElementById('ordersList');
  const notesWrap = document.getElementById('notesWrap') || document.getElementById('notesList');
  const loadingOverlay = document.getElementById('loadingOverlay');
  let lastOrders = [];
  let lastCustomer = null;
  const STATUS_MAP = {
    0: 'Mới',
    17: 'Chờ xác nhận',
    11: 'Chờ hàng',
    12: 'Chờ in',
    13: 'Đã in',
    20: 'Đã đặt hàng',
    1: 'Đã xác nhận',
    8: 'Đang đóng hàng',
    9: 'Chờ chuyển hàng',
    2: 'Đã gửi hàng',
    3: 'Đã nhận',
    16: 'Đã thu tiền',
    4: 'Đang hoàn',
    15: 'Hoàn một phần',
    5: 'Đã hoàn',
    6: 'Đã hủy',
    7: 'Đã xóa'
  };

  function showLoading(show) { if (loadingOverlay) loadingOverlay.style.display = show ? 'flex' : 'none'; }

  function sanitizePhone(value) { return (value || '').replace(/\D/g, '').trim(); }

  function formatDate(str) {
    if (!str) return '-';
    const date = new Date(str);
    if (isNaN(date.getTime())) return str;
    return date.toLocaleString('vi-VN');
  }

  function escapeHtml(s) { return (s == null) ? '' : String(s).replace(/[&<>"']/g, m => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[m])); }

  function renderCustomer(customer) {
    if (!customer) { if (customerCard) customerCard.style.display = 'none'; return; }
    lastCustomer = customer;
    if (customerInfo) {
      const succeedCount = customer.succeedOrderCount || 0;
      const customerStatus = succeedCount === 0 ? 'Khách mới' : 'Khách cũ';
      customerInfo.innerHTML = `
            <div class="w-20 h-20 mx-auto -mt-10 bg-white rounded-full p-1 shadow-md">
                <img src="https://api.dicebear.com/9.x/avataaars/svg?seed=Amaya&backgroundColor=65c9ff,b6e3f4&backgroundType=solid,gradientLinear&clothingGraphic=diamond&eyebrows=default,defaultNatural&eyes=default&facialHair[]&facialHairColor[]"
                    alt="Avatar" class="w-full h-full rounded-full">
            </div>
            <h2 class="mt-2 text-xl font-bold text-slate-800" >${escapeHtml(customer.name || '-')}</h2>
            <div class="flex justify-center items-center gap-2 text-slate-500 mt-1 mb-4">
                <span class="font-bold text-slate-700 text-lg">${escapeHtml(customer.phone || '-')}</span>
                <button class="text-brand-600 bg-brand-50 px-2 py-0.5 rounded text-xs hover:bg-brand-100"><i class="fa-regular fa-copy"></i></button>
            </div>
            <div class="grid grid-cols-2 gap-3 mb-4">
                <div class="bg-slate-50 p-2 rounded border border-slate-100">
                    <span class="block text-[10px] text-slate-400 uppercase font-bold">Thành công</span>
                    <span class="block text-xl font-bold text-emerald-600">${escapeHtml(String(customer.succeedOrderCount || 0))}</span>
                </div>
                <div class="bg-slate-50 p-2 rounded border border-slate-100">
                    <span class="block text-[10px] text-slate-400 uppercase font-bold">Trạng thái</span>
                    <span class="block text-base font-bold text-slate-700 mt-0.5">${escapeHtml(customerStatus)}</span>
                </div>
            </div>
            <div class="text-left space-y-3 border-t border-slate-100 pt-3">
                <div>
                    <span class="text-[10px] text-slate-400 font-bold uppercase">Customer ID</span>
                    <div class="bg-slate-100 text-slate-600 text-[11px] p-1.5 rounded font-mono truncate mt-1">${escapeHtml(customer.customerId || '-')}</div>
                </div>
                <div>
                    <span class="text-[10px] text-slate-400 font-bold uppercase">Địa chỉ</span>
                    <p class="text-sm text-slate-500 italic mt-0.5">${escapeHtml(customer.fullAddress || '-')}</p>
                </div>
            </div>
      `;
      if (customerCard) customerCard.style.display = 'block';
    }
  }

  // function renderSummary(summary) {
  //   const summaryInfo = document.getElementById('summaryInfo');
  //   if(!summary) { if (summaryInfo) summaryInfo.innerHTML = '<span class="muted">Không có dữ liệu</span>'; return; }
  //   if (summaryInfo) summaryInfo.innerHTML = `
  //     <div><strong>Tổng đơn:</strong> ${summary.totalOrders ?? 0}</div>
  //     <div><strong>Giao thành công:</strong> ${summary.deliveredOrders ?? 0}</div>
  //     <div><strong>Hoàn/Trả:</strong> ${summary.returnedOrders ?? 0}</div>
  //     <div><strong>Đã chi:</strong> ${summary.totalSpent ?? 0}</div>
  //     <div><strong>COD:</strong> ${summary.totalCOD ?? 0}</div>
  //     <div><strong>COD đã đối soát:</strong> ${summary.reconciledCOD ?? 0}</div>
  //   `;
  // }

  function itemsHtml(items) {
    if (!items || !items.length) return `<span class="text-slate-400 text-sm">Không có sản phẩm</span>`;
    return items.map(item => `<span class="bg-blue-50 text-blue-700 text-sm font-medium px-2 py-0.5 rounded border border-blue-100">${escapeHtml(item.name)} (${escapeHtml(String(item.quantity||0))})</span>`).join('');
  }

  function renderOrders(orders) {
    console.log('render order');
    lastOrders = orders || [];
    // populate left product summary element
    try {
      const leftEl = document.getElementById('leftProductSummaryContent');
      if (leftEl) {
        // Aggregate purchased product counts for orders with status = 3 (Đã nhận)
        const receivedOrders = (orders || []).filter(o => {
          const s = (typeof o.status === 'number') ? o.status : (o.status ? Number(o.status) : null);
          return s === 3;
        });
        const productCounts = {};
        receivedOrders.forEach(o => {
          (o.items || []).forEach(it => {
            const name = it.name || it.product_name || it.productName || 'Unknown';
            const qty = Number(it.quantity || 0);
            productCounts[name] = (productCounts[name] || 0) + qty;
          });
        });
        const keys = Object.keys(productCounts);
        if (keys.length === 0) {
          leftEl.innerHTML = '<span class=\"text-slate-400\">Không có sản phẩm đã nhận</span>';
        } else {
          leftEl.innerHTML = '<ul class=\"list-disc pl-5\">' + keys.map(k => `<li>${escapeHtml(k)} — <strong>${productCounts[k]}</strong></li>`).join('') + '</ul>';
        }
      }
    } catch (e) {
      console.warn('Could not render left product summary', e);
    }
    if (!orders || orders.length === 0) {
      if (ordersWrap) ordersWrap.innerHTML = '<span class="text-slate-400 text-sm">Không có dữ liệu</span>';
      return;
    }
    const totalOrders = orders.length;
    // Aggregate purchased product counts for orders with status = 3 (Đã nhận)
    const receivedOrders = (orders || []).filter(o => {
      const s = (typeof o.status === 'number') ? o.status : (o.status ? Number(o.status) : null);
      return s === 3;
    });
    const productCounts = {};
    receivedOrders.forEach(o => {
      (o.items || []).forEach(it => {
        const name = it.name || it.product_name || it.productName || 'Unknown';
        const qty = Number(it.quantity || 0);
        productCounts[name] = (productCounts[name] || 0) + qty;
      });
    });

    let summaryHtml = '';
    const productKeys = Object.keys(productCounts);
    if (productKeys.length > 0) {
      const rows = productKeys.map(k => `<li class="text-sm text-slate-600">${escapeHtml(k)} — <strong>${productCounts[k]}</strong></li>`).join('');
      summaryHtml = `
        <div class="p-3 mb-3 bg-white rounded border border-slate-100">
          <h4 class="font-bold text-sm mb-2">Tổng hợp sản phẩm đã mua (Đã nhận)</h4>
          <ul class="list-disc pl-5 space-y-1">${rows}</ul>
        </div>
      `;
    }

    let html = `
    <div class="flex flex-col bg-white rounded-xl shadow-sm border border-slate-200 h-full overflow-hidden">
        <div class="px-5 py-3 border-b border-slate-100 bg-white sticky top-0 z-10 flex justify-between items-center">
            <h3 class="font-bold text-lg text-slate-800">
                <i class="fa-solid fa-box-open text-brand-500 mr-2"></i>Lịch sử mua hàng
            </h3>
            <span class="text-sm text-slate-400 font-medium">${totalOrders} đơn hàng</span>
        </div>
        <div class="p-3 space-y-3 overflow-y-auto custom-scrollbar flex-1 bg-slate-50/30">
        ${summaryHtml}
    `;
    orders.forEach(o => {
      // ==== Status mapping ====
      const status = (typeof o.status === 'number') ? o.status : (o.status ? Number(o.status) : null);
      // Skip canceled orders (status = 6)
      if (status === 6) return;
      let statusText = status != null ? (STATUS_MAP[status] || String(status)) : '-';
      let statusClass = 'bg-slate-100 text-slate-600 border-slate-200';
      if (status === 3) {
        statusClass = 'bg-emerald-50 text-emerald-600 border-emerald-100';
      } else if (status === 6) {
        statusClass = 'bg-red-50 text-white-600 border-red-100';
      } else if (status === 1) {
        statusClass = 'bg-blue-100 text-white-600 border-blue-100';
      }else if (status === 11) {
        statusClass = 'bg-yellow-50 text-white-600 border-yellow-100';
      }else if (status === 9) {
        statusClass = 'bg-pink-50 text-white-600 border-yellow-100';
      }else if (status === 5) {
        statusClass = 'bg-yellow-50 text-yellow-600 border-yellow-100';
      }else if (status === 8) {
        statusClass = 'bg-yellow-50 text-yellow-600 border-yellow-100';
      }else if (status === 4) {
        statusClass = 'bg-yellow-50 text-yellow-600 border-yellow-100';
      }else{
        statusClass = 'bg-yellow-50 text-yellow-600 border-yellow-100';
      }

      // ==== Items ====
      let itemsHtmlStr = '';
      if (o.items && o.items.length > 0) {
        itemsHtmlStr = o.items.map(item => `
          <span class="bg-blue-50 text-blue-700 text-sm font-medium px-2 py-0.5 rounded border border-blue-100">
              ${escapeHtml(item.name)} (${escapeHtml(String(item.quantity))})
          </span>
        `).join('');
      } else {
        itemsHtmlStr = `<span class="text-slate-400 text-sm">Không có sản phẩm</span>`;
      }

      // ==== Time ====
      const dateTime = formatDate(o.timeAssignSeller);
      const orderId = o.orderId ?? o.systemId ?? '-';
      // determine sale and cskh per rules (uses orderSourcesName and assigningCareName)
      let saleName = o.assigningSellerName || '-';
      let cskhName = o.assigningCareName || '-';
      if (o.orderSourcesName && typeof o.orderSourcesName === 'string') {
        const src = o.orderSourcesName.toLowerCase();
        if (src.includes('facebook')) {
          saleName = o.assigningCareName || saleName;
          cskhName = o.assigningSellerName || '-';
        } else if (src.includes('zalo')) {
          saleName = o.assigningSellerName || '-';
          cskhName = saleName;
        }
      }

      html += `
        <div class="bg-white border border-slate-200 rounded-lg p-3 hover:border-brand-300 hover:shadow-md transition">
            <div class="flex justify-between items-start mb-2">
                <div>
                    <div class="text-xs font-bold text-slate-400 uppercase">#${o.orderId ? (o.orderLink ?
                                `<a href="${o.orderLink}" target="_blank" rel="noopener noreferrer">${escapeHtml(o.orderId)}</a>`
                                :
                                `<a href="https://pos.pages.fm/shop/1546758/order?order_id=${encodeURIComponent(o.orderId)}" target="_blank" rel="noopener noreferrer">${escapeHtml(o.orderId)}</a>`) : '-'}</div>
                    <h4 class="font-bold text-slate-800 text-base">${dateTime}</h4>
                    <div class="mt-1">
                      <button data-order-id="${escapeHtml(o.orderId)}" class="order-open text-sm text-brand-600 hover:underline">Chi tiết</button>
                    </div>
                </div>
                <span class="text-xs px-2 py-0.5 rounded border font-bold ${statusClass}">
                    ${statusText}
                </span>
            </div>
            <div class="flex flex-wrap gap-1.5 mb-2">
                ${itemsHtmlStr}
            </div>
            <div class="text-sm text-slate-400 pt-2 border-t border-slate-50">
                <div><span class="font-medium">CSKH:</span> ${escapeHtml(saleName)}</div>
                <div class="mt-1"><span class="font-medium">Sale:</span> ${escapeHtml(cskhName)}</div>
            </div>
        </div>
        `;
    });
    html += `
        </div>
    </div>
    `;
    if (ordersWrap) ordersWrap.innerHTML = html;
  }

  function renderNotes(notes) {
    console.log('render notes', notes);
    if (!notes || notes.length === 0) {
      if (notesWrap) notesWrap.innerHTML = '<span class="muted">Không có dữ liệu</span>';
      return;
    }
    let html = `<div class="flex flex-col bg-white rounded-xl shadow-sm border border-slate-200 h-full overflow-hidden" >
      <div class="px-5 py-3 border-b border-slate-100 bg-white sticky top-0 z-10">
        <h3 class="font-bold text-lg text-slate-800"><i class="fa-regular fa-comments text-brand-500 mr-2"></i>Nhật ký chăm sóc</h3>
      </div>
      <div class="p-4 overflow-y-auto custom-scrollbar flex-1 bg-slate-50/30">
        <div class="relative timeline-line space-y-6 pl-2">
        `;
    notes.forEach(n => {
      if (n.length === 0) {
        html += `<span class="text-slate-400 text-sm">Khách hàng chưa có note</span>`;
      } else {
        html += `  <div class="relative pl-8">
          <div class="absolute left-0 top-1 w-7 h-7 bg-white border-2 border-brand-500 rounded-full flex items-center justify-center z-10 shadow-sm">
            <i class="fa-solid fa-check text-brand-500 text-xs"></i>
          </div>  <div>
            ${(() => {
              // try to find matching order to get date
              const match = lastOrders.find(o => String(o.orderId) === String(n.orderId) || String(o.systemId) === String(n.orderId));
              return `<span class="text-xs text-slate-400 font-bold uppercase tracking-wider">${match ? formatDate(match.timeAssignSeller) : escapeHtml(n.orderId || '-')}</span>`;
            })()}
            <div class="bg-white p-3 rounded-lg border border-slate-200 shadow-sm mt-1 group hover:border-brand-200 transition">
              <p class="text-slate-800 text-base font-medium">${escapeHtml(n.message || '-')}</p>
              <p class="text-xs text-slate-400 mt-1">${escapeHtml(n.orderId || '-')}</p>
            </div>
          </div> </div>`;
      };
    });
    html += `
        </div>
      </div>
    </div>`;
    if (notesWrap) notesWrap.innerHTML = html;
    // Also populate modal content if present
    try {
      const notesModalContent = document.getElementById('notesModalContent');
      if (notesModalContent) notesModalContent.innerHTML = html;
    } catch (e) {}
    // Move any leftover "Trao đổi" block from left column into right conversationsWrap
    try {
      const convWrap = document.getElementById('conversationsWrap') || document.getElementById('conversationsList');
      if (convWrap) {
        const candidates = Array.from(document.querySelectorAll('div')).filter(el => {
          const t = (el.textContent || '').trim();
          return t === 'Trao đổi' || t.startsWith('Trao đổi');
        });
        candidates.forEach(el => {
          // find the nearest container box to move (rounded box)
          let box = el.closest && el.closest('.bg-white, .rounded-xl');
          if (!box) box = el;
          if (box && !convWrap.contains(box)) {
            try { convWrap.appendChild(box); console.log('Moved existing Trao đổi block to right'); } catch(e){}
          }
        });
      }
    } catch (e) {}
  }

  function renderConversations(conversations) {
    const list = document.getElementById('conversationsList') || document.getElementById('conversationsWrap') || document.getElementById('conversationsList');
    if (!list) return;
    if (!conversations || conversations.length === 0) {
      list.innerHTML = '<span class="text-slate-400">Chưa có trao đổi</span>';
      return;
    }
    let html = `<div class="flex flex-col gap-3">`;
    conversations.forEach(c => {
      html += `
        <div class="bg-white p-3 rounded border border-slate-100">
          <div class="text-sm text-slate-700 mb-1">${escapeHtml(c.content || '')}</div>
          <div class="text-xs text-slate-400">Người: ${escapeHtml(c.name || '-')} • ${escapeHtml(c.createdAt == null ? '' : String(c.createdAt))}</div>
        </div>
      `;
    });
    html += `</div>`;
    list.innerHTML = html;
  }

  async function doSearch() {
    console.log(phoneInput ? phoneInput.value : 'no input');
    const phone = sanitizePhone(phoneInput ? phoneInput.value : '');
    if (!phone) {
      alert('Vui lòng nhập số điện thoại hợp lệ');
      return;
    }
    // show main grid and hide empty state when searching
    try { const mainGrid = document.getElementById('mainGrid'); const emptyState = document.getElementById('emptyState'); if (mainGrid) mainGrid.classList.remove('hidden'); if (emptyState) emptyState.style.display = 'none'; } catch(e) {}
    if (phoneInput) phoneInput.value = phone;
    // show loading overlay
    showLoading(true);
    showLoading(true);
    if (searchBtn) searchBtn.disabled = true;
    try {
      console.log('Clicked search button');
      const res = await fetch(`/api/search-info?phone=${encodeURIComponent(phone)}`);
      const data = await res.json();
      if (!res.ok || data.error || data.message) {
        alert(data.error || data.message || 'Không thể tra cứu');
        if (customerCard) customerCard.style.display = 'none';
        if (ordersWrap) ordersWrap.innerHTML = '<span class="muted">Không có dữ liệu</span>';
        if (notesWrap) notesWrap.innerHTML = '<span class="muted">Không có dữ liệu</span>';
        return;
      }
      renderCustomer(data.customer);
      renderOrders(data.orders);
      renderNotes(data.customer ? data.customer.notes : []);
      // fetch trao đổi: ưu tiên gọi bảng cụ thể nếu baseId/tableId có sẵn (từ URL params hoặc global),
      // ngược lại fallback sang /api/exchanges (hiện tại server-side aggregate)
      try {
        const urlParams = new URLSearchParams(window.location.search);
        const baseIdParam = urlParams.get('baseId') || window._preferredBaseId || '';
        const tableIdParam = urlParams.get('tableId') || window._preferredTableId || '';

        if (baseIdParam && tableIdParam) {
          // call our new endpoint (POST with query params)
          const convRes = await fetch(`/api/lark/search-by-table?baseId=${encodeURIComponent(baseIdParam)}&tableId=${encodeURIComponent(tableIdParam)}&phone=${encodeURIComponent(phone)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' }
          });
          const convJson = await convRes.json();
          console.log('[/api/lark/search-by-table] response:', { ok: convRes.ok, status: convRes.status, body: convJson });
          if (convRes.ok && convJson && convJson.data) {
            const mapped = convJson.data.map(c => ({
              content: c.content,
              name: c.customerName || '',
              createdAt: c.createdAt,
              baseId: c.baseId || baseIdParam,
              tableId: c.tableId || tableIdParam,
              linkRecordIds: c.linkRecordIds || []
            }));
            // remember context (useful when creating new record)
            window._lastExchangeContext = mapped.length ? mapped[0] : { baseId: baseIdParam, tableId: tableIdParam, linkRecordIds: [] };
            renderConversations(mapped);
          } else {
            // fallback to aggregate endpoint if table-specific call returned nothing
            const convRes2 = await fetch(`/api/exchanges?phone=${encodeURIComponent(phone)}&customerName=${encodeURIComponent(data.customer ? data.customer.name : '')}`);
            const convJson2 = await convRes2.json();
            console.log('[/api/exchanges] fallback response:', { ok: convRes2.ok, status: convRes2.status, body: convJson2 });
            if (convRes2.ok && convJson2 && convJson2.data) {
              const mapped2 = convJson2.data.map(c => ({ content: c.content, name: c.customerName || '', createdAt: c.createdAt, baseId: c.baseId, tableId: c.tableId, linkRecordIds: c.linkRecordIds || [] }));
              window._lastExchangeContext = mapped2.length ? mapped2[0] : null;
              renderConversations(mapped2);
            } else {
              const sampleConvos = [
                { content: 'Khách hỏi về thuốc A, đã tư vấn', name: data.customer ? data.customer.name : 'Khách lạ', createdAt: Date.now() - 86400000 },
                { content: 'CSKH đã gọi và xác nhận đơn', name: 'CSKH Nguyễn', createdAt: Date.now() - 3600000 }
              ];
              renderConversations(sampleConvos);
            }
          }
        } else {
          // no specific table context -> use existing aggregate endpoint
          const convRes = await fetch(`/api/exchanges?phone=${encodeURIComponent(phone)}&customerName=${encodeURIComponent(data.customer ? data.customer.name : '')}`);
          const convJson = await convRes.json();
          console.log('[/api/exchanges] response:', { ok: convRes.ok, status: convRes.status, body: convJson });
          if (convRes.ok && convJson && convJson.data) {
            try { convJson.data.forEach((c, idx) => console.log(`[exchange][${idx}] createdAt raw:`, c.createdAt, 'type:', typeof c.createdAt, 'full:', c)); } catch (e) {}
            const mapped = convJson.data.map(c => ({ content: c.content, name: c.customerName || '', createdAt: c.createdAt, baseId: c.baseId, tableId: c.tableId, linkRecordIds: c.linkRecordIds || [] }));
            window._lastExchangeContext = mapped.length ? mapped[0] : null;
            renderConversations(mapped);
          } else {
            const sampleConvos = [
              { content: 'Khách hỏi về thuốc A, đã tư vấn', name: data.customer ? data.customer.name : 'Khách lạ', createdAt: Date.now() - 86400000 },
              { content: 'CSKH đã gọi và xác nhận đơn', name: 'CSKH Nguyễn', createdAt: Date.now() - 3600000 }
            ];
            renderConversations(sampleConvos);
          }
        }
      } catch (e) {
        console.warn('Exchange fetch error, showing samples', e);
        const sampleConvos = [
          { content: 'Khách hỏi về thuốc A, đã tư vấn', name: data.customer ? data.customer.name : 'Khách lạ', createdAt: Date.now() - 86400000 },
          { content: 'CSKH đã gọi và xác nhận đơn', name: 'CSKH Nguyễn', createdAt: Date.now() - 3600000 }
        ];
        renderConversations(sampleConvos);
      }
    } catch (err) {
      console.error(err);
      alert('Lỗi khi tra cứu: ' + err.message);
    } finally {
      showLoading(false);
      if (searchBtn) searchBtn.disabled = false;
    }
  }

  function init() {
    if (searchBtn) searchBtn.addEventListener('click', doSearch);
    if (phoneInput) phoneInput.addEventListener('keydown', e => { if (e.key === 'Enter') doSearch(); });
    window._searchLoadPhone = doSearch;
    // initial empty state handling
    try {
      const mainGrid = document.getElementById('mainGrid');
      const emptyState = document.getElementById('emptyState');
      const q = (new URLSearchParams(window.location.search)).get('phone') || '';
      if ((phoneInput && phoneInput.value && phoneInput.value.trim()) || q.trim()) {
        if (mainGrid) mainGrid.classList.remove('hidden');
        if (emptyState) emptyState.style.display = 'none';
      } else {
        if (mainGrid) mainGrid.classList.add('hidden');
        if (emptyState) emptyState.style.display = 'flex';
      }
    } catch (e) {}
    // Modal handlers
    const orderModal = document.getElementById('orderModal');
    const orderModalOverlay = document.getElementById('orderModalOverlay');
    const orderModalClose = document.getElementById('orderModalClose');
    const orderDetails = document.getElementById('orderDetails');
    const orderNoteInput = document.getElementById('orderNoteInput');
    const orderNoteSubmit = document.getElementById('orderNoteSubmit');
    const orderNoteCancel = document.getElementById('orderNoteCancel');

    function closeModal() {
      if (orderModal) {
        orderModal.classList.add('hidden');
        orderModal.classList.remove('flex');
      }
      if (orderNoteInput) orderNoteInput.value = '';
    }

    function openModalForOrderId(ordId) {
      if (!ordId) return;
      const ord = lastOrders.find(o => String(o.orderId) === String(ordId) || String(o.systemId) === String(ordId));
      if (!ord) {
        alert('Không tìm thấy đơn hàng chi tiết');
        return;
      }
      // render details
      const items = (ord.items || []).map(it => `<li>${escapeHtml(it.name)} — ${escapeHtml(String(it.quantity))}</li>`).join('');
      orderDetails.innerHTML = `
        <div><strong>Mã đơn:</strong> ${escapeHtml(ord.orderId || ord.systemId || '-')}</div>
        <div class="mt-2"><strong>Thời gian:</strong> ${formatDate(ord.timeAssignSeller)}</div>
        <div class="mt-2"><strong>Trạng thái:</strong> ${escapeHtml(STATUS_MAP[Number(ord.status)] || String(ord.status || '-'))}</div>
        <div class="mt-2"><strong>Sản phẩm:</strong><ul class="list-disc pl-5 mt-1">${items}</ul></div>
        <div class="mt-2"><strong>Sale:</strong> ${escapeHtml(ord.assigningSellerName || '-')}</div>
        <div class="mt-1"><strong>CSKH:</strong> ${escapeHtml(ord.assigningCareName || '-')}</div>
      `;
      if (orderModal) {
        orderModal.classList.remove('hidden');
        orderModal.classList.add('flex');
      }
      // focus textarea
      if (orderNoteInput) orderNoteInput.focus();

      // submit handler
      if (orderNoteSubmit) {
        orderNoteSubmit.onclick = () => {
          const msg = orderNoteInput.value && orderNoteInput.value.trim();
          if (!msg) { alert('Nhập nội dung note'); return; }
          // only log locally (no server call)
          console.log('Note submitted (local only):', {
            message: msg,
            customerId: lastCustomer ? lastCustomer.customerId : null,
            orderId: ord.orderId || ord.systemId
          });
          closeModal();
          // refresh data view
          doSearch();
        };
      }
    }

    if (orderModalClose) orderModalClose.addEventListener('click', closeModal);
    if (orderModalOverlay) orderModalOverlay.addEventListener('click', closeModal);
    if (orderNoteCancel) orderNoteCancel.addEventListener('click', closeModal);

    // delegate clicks from ordersWrap
    if (ordersWrap) {
      ordersWrap.addEventListener('click', (e) => {
        const btn = e.target.closest && e.target.closest('.order-open');
        if (btn) {
          e.preventDefault();
          const id = btn.getAttribute('data-order-id');
          openModalForOrderId(id);
        }
      });
    }
    // notes toggle: collapsed by default
    try {
      const notesToggle = document.getElementById('notesToggle');
      const notesWrapEl = document.getElementById('notesWrap');
      if (notesToggle && notesWrapEl) {
        // keep inline notes hidden; use modal to show notes
        notesWrapEl.classList.add('hidden');
        notesToggle.textContent = 'Mở';
        const notesModal = document.getElementById('notesModal');
        const notesModalClose = document.getElementById('notesModalClose');
        const notesModalOverlay = document.getElementById('notesModalOverlay');
        const notesHeader = document.getElementById('notesHeader');
        function openNotesModal() {
          // ensure modal content is up-to-date (renderNotes already populates it)
          if (notesModal) {
            notesModal.classList.remove('hidden');
            notesModal.classList.add('flex');
          }
          notesToggle.textContent = 'Ẩn';
        }
        function closeNotesModal() {
          if (notesModal) {
            notesModal.classList.add('hidden');
            notesModal.classList.remove('flex');
          }
          notesToggle.textContent = 'Mở';
        }
        notesToggle.addEventListener('click', () => {
          const visible = !(notesModal && notesModal.classList.contains('hidden'));
          if (visible) closeNotesModal(); else openNotesModal();
        });
        if (notesHeader) {
          notesHeader.addEventListener('click', (ev) => {
            // if clicked the toggle button, ignore (button handler covers)
            if (ev.target && ev.target.closest && ev.target.closest('#notesToggle')) return;
            openNotesModal();
          });
        }
        if (notesModalClose) notesModalClose.addEventListener('click', closeNotesModal);
        if (notesModalOverlay) notesModalOverlay.addEventListener('click', closeNotesModal);
      }
    } catch (e) {}
  // Exchange add modal handlers
  try {
    const exchangeModal = document.getElementById('exchangeModal');
    const exchangeModalOverlay = document.getElementById('exchangeModalOverlay');
    const exchangeModalClose = document.getElementById('exchangeModalClose');
    const exchangeInput = document.getElementById('exchangeInput');
    const exchangeSubmit = document.getElementById('exchangeSubmit');
    const exchangeCancel = document.getElementById('exchangeCancel');
    const exchangeAddBtn = document.getElementById('exchangeAddBtn');
    function openExchangeModal() {
      if (exchangeModal) {
        exchangeModal.classList.remove('hidden');
        exchangeModal.classList.add('flex');
      }
      if (exchangeInput) exchangeInput.value = '';
      if (exchangeInput) exchangeInput.focus();
    }
    function closeExchangeModal() {
      if (exchangeModal) {
        exchangeModal.classList.add('hidden');
        exchangeModal.classList.remove('flex');
      }
    }
    if (exchangeAddBtn) exchangeAddBtn.addEventListener('click', (e) => { e.preventDefault(); openExchangeModal(); });
    if (exchangeModalClose) exchangeModalClose.addEventListener('click', closeExchangeModal);
    if (exchangeModalOverlay) exchangeModalOverlay.addEventListener('click', closeExchangeModal);
    if (exchangeCancel) exchangeCancel.addEventListener('click', closeExchangeModal);
    if (exchangeSubmit) {
      exchangeSubmit.addEventListener('click', () => {
        const msg = exchangeInput ? exchangeInput.value && exchangeInput.value.trim() : '';
        if (!msg) { alert('Nhập nội dung trao đổi'); return; }
        // Use current timestamp for createdAt
        const dateVal = Date.now();
        const newRec = { content: 'PK: ' + msg, customerName: lastCustomer ? lastCustomer.name : '', createdAt: dateVal, Ngày: dateVal };
        // Try to create via backend into Lark if we have table context
        try {
          const ctx = window._lastExchangeContext || {};
            if (ctx.baseId && ctx.tableId) {
            const payload = { content: 'PK: ' + msg, ngay: dateVal, linkRecordIds: ctx.linkRecordIds || [] };
            fetch(`/api/lark/create-record?baseId=${encodeURIComponent(ctx.baseId)}&tableId=${encodeURIComponent(ctx.tableId)}`, {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify(payload)
            }).then(r => r.json()).then(j => {
              if (j && j.code === 0) {
                // prepend created record to UI
                try {
                  const list = document.getElementById('conversationsList');
                  if (list) {
                    const node = document.createElement('div');
                    node.className = 'bg-white p-3 rounded border border-slate-100 mb-3';
                    const createdRaw = newRec.createdAt == null ? '' : String(newRec.createdAt);
                    node.innerHTML = `<div class="text-sm text-slate-700 mb-1">${escapeHtml(newRec.content)}</div><div class="text-xs text-slate-400">Người: ${escapeHtml(newRec.customerName || '-')} • ${escapeHtml(createdRaw)}</div>`;
                    list.insertBefore(node, list.firstChild);
                  }
                } catch (e) { console.warn('Could not append exchange locally', e); }
              } else {
                console.warn('Create record failed, fallback to local append', j);
              }
            }).catch(e => console.warn('Create record request failed, fallback to local', e));
          } else {
            // no table context — fallback to local-only behaviour
            try {
              const list = document.getElementById('conversationsList');
              if (list) {
                const node = document.createElement('div');
                node.className = 'bg-white p-3 rounded border border-slate-100 mb-3';
                const createdRaw = newRec.createdAt == null ? '' : String(newRec.createdAt);
                node.innerHTML = `<div class="text-sm text-slate-700 mb-1">${escapeHtml(newRec.content)}</div><div class="text-xs text-slate-400">Người: ${escapeHtml(newRec.customerName || '-')} • ${escapeHtml(createdRaw)}</div>`;
                list.insertBefore(node, list.firstChild);
              }
            } catch (e) { console.warn('Could not append exchange locally', e); }
          }
        } catch (e) { console.warn('Error creating exchange', e); }
        closeExchangeModal();
      });
    }
  } catch (e) {}
    // render fake conversations immediately for UI testing
    try {
      const sampleConvosInitial = [
        { content: 'Khách hỏi về chương trình khuyến mãi, đã tư vấn', name: 'Trần A', createdAt: Date.now() - 1000 * 60 * 60 * 24 },
        { content: 'CSKH gọi nhắc lịch lấy thuốc', name: 'CSKH B', createdAt: Date.now() - 1000 * 60 * 60 * 4 },
        { content: 'Khách xác nhận nhận thuốc', name: 'Khách C', createdAt: Date.now() - 1000 * 60 * 30 }
      ];
      renderConversations(sampleConvosInitial);
    } catch (e) {}
    // replace initial sample with server-driven later; keep samples for UI when no server
  }

  init();
})();


