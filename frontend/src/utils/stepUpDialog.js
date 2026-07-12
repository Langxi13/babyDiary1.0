let dialogOpener = null

export function registerStepUpDialog(opener) {
  dialogOpener = opener
  return () => {
    if (dialogOpener === opener) dialogOpener = null
  }
}

export function openStepUpDialog() {
  if (!dialogOpener) {
    return Promise.reject(new Error('身份验证弹窗尚未就绪'))
  }
  return dialogOpener()
}
