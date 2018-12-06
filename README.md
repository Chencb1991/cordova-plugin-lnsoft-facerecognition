
# cordova-plugin-lnsoft-facerecognition

Apache Cordova 人脸识别插件。

## 说明 

启动扫描控件的方法：

```js
navigator.lnsoft.startPreview(callback, [options]);
```

其中：

* __callback__ 为成功回调方法，接收 `ArrayBuffer` 作为回调参数。
* __options__ 为参数选项，目前仅支持传入一个小数，表示扫脸方框的占比。


完成扫描识别后，要及时销毁控件：

```js
navigator.lnsoft.removeViews()
```

页面效果使用 web 技术开发实现，将 webview 中页面的 `html`、`body` 等的 `background-color` 设置为 `transparent` ，即可将相机控件的 View 组件显示出来，在其上层开发页面即可。

## 示例

```js
if (!navigator.lnsoft || !navigator.lnsoft.startPreview) {
  this.$toast('当前设备不支持身份验证功能!');
  return;
}
navigator.lnsoft.startPreview(authenticated, [0.75]);

function authenticated (imageBuffer) {
  let file = new File(imageBuffer, 'face.jpg',
      {type: 'image/jpeg', lastModified: Date.now()});

  let formData = new FormData();
  formData.append('file', file);
  this.$upload('/upload', formData)
    .then(res => {
      console.log(JSON.stringify(res));
    });

  navigator.lnsoft.removeViews();
} 

```


