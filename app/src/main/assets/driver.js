// Pilote de connexion. __E / __P (identifiants) sont définis juste avant par l'app,
// uniquement sur les pages de connexion Microsoft.
(function (E, P) {
  function vis(el) { return !!el && el.offsetParent !== null; }
  function setv(el, v) {
    var d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
    el.focus(); d.set.call(el, v);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
  function once(key, ms) {
    var now = Date.now(); window.__bsT = window.__bsT || {};
    if (window.__bsT[key] && now - window.__bsT[key] < ms) return false;
    window.__bsT[key] = now; return true;
  }
  function out(o) { return JSON.stringify(o); }
  function clickNext() {
    var b = document.querySelector('#idSIButton9') || document.querySelector('input[type=submit]');
    if (b) b.click();
  }
  var h = location.hostname;
  var t = document.body ? document.body.innerText : '';

  if (h.indexOf('builders-ingenieurs.fr') >= 0) {
    if (window.PrimeFaces && PrimeFaces.widgets && PrimeFaces.widgets.schedule && PrimeFaces.widgets.schedule.jqc)
      return out({ s: 'ready' });
    var as = [].slice.call(document.querySelectorAll('a'));
    var find = function (txt) { return as.find(function (a) { return a.textContent.trim() === txt; }); };
    var lg = find('Se connecter');
    if (lg) { if (once('sso', 20000)) lg.click(); return out({ s: 'sso' }); }
    // Le lien « Mon Planning » n'existe qu'une fois le menu « Planning » ouvert.
    var mp = find('Mon Planning');
    if (mp) {
      if (once('mon', 15000)) mp.click();
      return out({ s: 'menu' });
    }
    var pl = find('Planning');
    if (pl) {
      if (once('plan', 5000)) pl.click();
      return out({ s: 'menu' });
    }
    var burger = document.querySelector('.menu-button, .topbar-menu-button, #menu-button, .layout-menu-button, a[class*=menu-button]');
    if (burger && once('burger', 8000)) burger.click();
    return out({ s: 'wait', m: 'portail' });
  }

  if (h.indexOf('microsoftonline') >= 0 || h.indexOf('login.live.com') >= 0 || h.indexOf('microsoft.com') >= 0) {
    // Validation Authenticator (avec ou sans numéro)
    var num = document.querySelector('#idRichContext_DisplaySign');
    if (vis(num)) return out({ s: 'mfa', n: num.textContent.trim() });
    if (/Approuver la demande de connexion|Approve sign in request|Ouvrez votre application Authenticator|Open your Authenticator app/i.test(t))
      return out({ s: 'mfa', n: '' });
    if (/Entrer le code|Enter code|Saisir le code/i.test(t) && document.querySelector('input[name=otc]'))
      return out({ s: 'need_user', m: 'code' });
    // Erreurs d'identifiants
    var errs = ['#passwordError', '#usernameError', '#idTD_Error'];
    for (var i = 0; i < errs.length; i++) {
      var er = document.querySelector(errs[i]);
      if (vis(er) && er.textContent.trim()) return out({ s: 'bad', m: er.textContent.trim() });
    }
    // « Rester connecté ? »
    if (document.querySelector('#KmsiCheckboxField') || /Rester connecté|Stay signed in/i.test(t)) {
      if (once('kmsi', 6000)) {
        var kc = document.querySelector('#KmsiCheckboxField'); if (kc && !kc.checked) kc.click();
        clickNext();
      }
      return out({ s: 'kmsi' });
    }
    // Mot de passe
    var pw = document.querySelector('input[name=passwd]');
    if (vis(pw)) {
      if (!P) return out({ s: 'need_user', m: 'creds' });
      if (once('pwd', 9000)) { setv(pw, P); setTimeout(clickNext, 400); return out({ s: 'pwd_sub' }); }
      return out({ s: 'pwd' });
    }
    // E-mail
    var em = document.querySelector('input[name=loginfmt]');
    if (vis(em)) {
      if (!E) return out({ s: 'need_user', m: 'creds' });
      if (once('email', 9000)) { setv(em, E); setTimeout(clickNext, 400); return out({ s: 'email_sub' }); }
      return out({ s: 'email' });
    }
    // Choix du compte
    var tiles = [].slice.call(document.querySelectorAll('[data-test-id], .table[role=button], #tilesHolder [role=button]'));
    var tile = tiles.find(function (x) { return E && x.textContent.toLowerCase().indexOf(E.toLowerCase()) >= 0 && vis(x); });
    if (tile) { if (once('tile', 8000)) tile.click(); return out({ s: 'picker' }); }
    if (tiles.length && /Choisir un compte|Pick an account/i.test(t)) {
      var other = document.querySelector('#otherTile'); if (other && once('other', 8000)) other.click();
      return out({ s: 'picker' });
    }
    return out({ s: 'wait', m: h });
  }
  return out({ s: 'wait', m: h });
})(typeof __E === 'undefined' ? '' : __E, typeof __P === 'undefined' ? '' : __P);
