/** Server-owned model/tool choices, shared by chat, review and preferences. */
(function () {
  const escape=window.MarkdownUtils.escapeHtml;
  async function loadModels(api, selectors) {
    const data=await api.getSystemModels();
    const allowed=Array.isArray(data?.allowedModels)?data.allowedModels.filter(value=>typeof value==='string'):[];
    selectors.filter(Boolean).forEach(select=>{
      const previous=select.value;
      select.innerHTML='<option value="">Mặc định hệ thống</option>'+allowed.map(model=>`<option value="${escape(model)}">${escape(model)}${model===data.activeModel?' (mặc định)':''}</option>`).join('');
      select.value=allowed.includes(previous)?previous:'';
    });
  }
  async function loadTools(api,container) {
    if(!container)return;
    const selected=new Set(Array.from(container.querySelectorAll('input:checked')).map(input=>input.value));
    const firstLoad=container.dataset.loaded!=='true';
    try {
      const tools=await api.getSystemTools();
      container.innerHTML=tools.filter(tool=>typeof tool.toolName==='string' && tool.enabled!==false).map(tool=>`<label class="tool-option"><input type="checkbox" name="taskTools" value="${escape(tool.toolName)}" ${firstLoad||selected.has(tool.toolName)?'checked':''}><span>${escape(tool.displayName || tool.toolName)}</span></label>`).join('') || '<p class="muted">Chưa có công cụ khả dụng từ server.</p>';
      container.dataset.loaded='true';
    } catch(error) {
      container.innerHTML='<p class="muted">Không tải được danh sách công cụ. Hãy làm mới trước khi review.</p>';
    }
  }
  window.ReviewForm={loadModels,loadTools};
})();
