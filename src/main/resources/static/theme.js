
    try {
      const savedTheme = localStorage.getItem('eventos-theme');
      if (savedTheme === 'light' || savedTheme === 'dark') document.documentElement.dataset.theme = savedTheme;
    } catch (_) { /* O tema escuro continua disponível sem armazenamento local. */ }
  
document.addEventListener('DOMContentLoaded', () => {
const button=document.getElementById('theme-toggle');
function update(){const dark=document.documentElement.dataset.theme==='dark';button.textContent=dark?'☀ Tema claro':'☾ Tema escuro';button.setAttribute('aria-label',dark?'Ativar tema claro':'Ativar tema escuro');}
button.addEventListener('click',()=>{const theme=document.documentElement.dataset.theme==='dark'?'light':'dark';document.documentElement.dataset.theme=theme;try{localStorage.setItem('eventos-theme',theme);}catch(_){} update();});update();
});
