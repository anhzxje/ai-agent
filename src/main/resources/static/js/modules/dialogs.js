/** Accessible focus lifecycle shared by all existing modal entry points. */
(function () {
  let active = null;
  let returnFocus = null;
  const selector = 'button:not(:disabled),a[href],input:not(:disabled):not([type="hidden"]),select:not(:disabled),textarea:not(:disabled),[tabindex="0"]';
  function focusable(panel) {
    return Array.from(panel.querySelectorAll(selector)).filter(node => node.getClientRects().length && !node.closest('[hidden]'));
  }
  function sync() {
    const opened = Array.from(document.querySelectorAll('.modal-backdrop.active'));
    const next = opened.at(-1) || null;
    const app = document.querySelector('.app-container');
    if (app) app.inert = !!next;
    document.querySelectorAll('.modal-backdrop').forEach(modal => {
      modal.setAttribute('aria-hidden', String(modal !== next));
      modal.inert = modal !== next;
    });
    if (next === active) return;
    if (next) {
      if (!active) returnFocus = document.activeElement;
      active = next;
      const panel = next.querySelector('.modal-panel') || next.firstElementChild;
      panel.tabIndex = -1;
      (focusable(panel)[0] || panel).focus();
    } else {
      active = null;
      if (returnFocus?.isConnected && returnFocus.getClientRects().length) returnFocus.focus();
      returnFocus = null;
    }
  }
  function init() {
    document.querySelectorAll('.modal-backdrop').forEach((modal,index) => {
      const panel=modal.querySelector('.modal-panel') || modal.firstElementChild;
      panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');
      const title=modal.querySelector('.modal-heading,h2,h3');
      if(title){if(!title.id)title.id=`dialog-title-${index}`;panel.setAttribute('aria-labelledby',title.id);}
      else panel.setAttribute('aria-label','Hộp thoại');
      new MutationObserver(sync).observe(modal,{attributes:true,attributeFilter:['class']});
      modal.addEventListener('click',event=>{if(event.target===modal)modal.classList.remove('active');});
    });
    document.addEventListener('keydown',event=>{
      if(!active)return;
      if(event.key==='Escape'){active.classList.remove('active');return;}
      if(event.key!=='Tab')return;
      const panel=active.querySelector('[role="dialog"]');
      const nodes=focusable(panel);
      const first=nodes[0] || panel,last=nodes.at(-1) || panel;
      if(!nodes.length){event.preventDefault();panel.focus();return;}
      if(event.shiftKey && (document.activeElement===first || document.activeElement===panel)){event.preventDefault();last.focus();}
      else if(!event.shiftKey && document.activeElement===last){event.preventDefault();first.focus();}
    });
    sync();
  }
  window.WorkspaceDialogs={init};
})();
