var exec = require('cordova/exec');
function FaceDetectPlugin(){

}
FaceDetectPlugin.prototype.startPreview = function (successCallback,options) {
    exec(successCallback, null, 'FaceDetectPlugin', 'startPreview',options);
};
FaceDetectPlugin.prototype.removeViews = function () {
    exec(null, null, 'FaceDetectPlugin', 'removeViews',null);
};
module.exports=new FaceDetectPlugin();