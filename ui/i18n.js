// Keep canonical option values and user content untouched when translating UI text.
const translatedNodes=new WeakMap();
const translatedAttributes=new WeakMap();
function tr(source) {
  const messages = preferences.locales?.messages?.[preferences.language || 'en'] || {};
  if (messages[source]) return messages[source];
  for (const prefix of ['Complete ', 'Reopen ', 'Edit ', 'Open ', 'Delete profile ', 'Rename profile ']) {
    if (source.startsWith(prefix) && messages[prefix+'{name}']) return messages[prefix+'{name}'].replace('{name}',source.slice(prefix.length));
  }
  return source;
}
const languageObserver=new MutationObserver(()=>localize());
function localize() {
  languageObserver.disconnect();
  document.querySelectorAll('option:not([value])').forEach(option=>option.setAttribute('value',option.value));
  const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
  while(walker.nextNode()) {
    const node=walker.currentNode;
    if(node.parentElement?.closest('script,style,textarea,[data-user-content],.task-notes,.journal-button,.spread-title>span,.profile-button'))continue;
    const previous=translatedNodes.get(node);
    const source=previous && node.nodeValue===previous.output ? previous.source : node.nodeValue;
    const key=source.trim();const output=source.replace(key,tr(key));
    if(node.nodeValue!==output)node.nodeValue=output;
    translatedNodes.set(node,{source,output});
  }
  for(const el of document.querySelectorAll('[placeholder],[aria-label]')) {
    if(el.closest('[data-user-content],[data-profile],[data-delete-profile]'))continue;
    const previous=translatedAttributes.get(el)||{};
    for(const name of ['placeholder','aria-label']) {if(!el.hasAttribute(name))continue;const value=el.getAttribute(name);const entry=previous[name];const source=entry && value===entry.output?entry.source:value;const output=tr(source);if(value!==output)el.setAttribute(name,output);previous[name]={source,output};}
    translatedAttributes.set(el,previous);
  }
  languageObserver.observe(document.body,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['placeholder','aria-label']});
}
localize();
