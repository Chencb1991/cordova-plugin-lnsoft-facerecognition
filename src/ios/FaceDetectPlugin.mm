/********* faceSelect.m Cordova Plugin Implementation *******/

#import <Cordova/CDV.h>
#import <AVFoundation/AVFoundation.h>
#import <pthread.h>
#import "ScanningView.h"
#define LFSCREEN_WIDTH [UIScreen mainScreen].bounds.size.width

#ifdef DEBUG
#define LFLog(fmt, ...) NSLog((@"%s [Line %d] " fmt), __PRETTY_FUNCTION__, __LINE__, ##__VA_ARGS__);
#else
#define LFLog(...)
#endif

static NSInteger MAXCOUT = 10;

inline NSString * imageFileDir(){
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory,NSUserDomainMask,YES);
    NSString *DirPath = [[paths objectAtIndex:0]stringByAppendingPathComponent:                          @"faceIdentifyImages"];
    BOOL dir;
    if (![[NSFileManager defaultManager]fileExistsAtPath:DirPath isDirectory:&dir]) {
        [[NSFileManager defaultManager]createDirectoryAtPath:DirPath withIntermediateDirectories:YES attributes:nil error:nil];
    }
    return DirPath;
}

inline NSString * faceFileDir(){
    NSArray *paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory,NSUserDomainMask,YES);
    NSString *DirPath = [[paths objectAtIndex:0]stringByAppendingPathComponent:                          @"face"];
    BOOL dir;
    if (![[NSFileManager defaultManager]fileExistsAtPath:DirPath isDirectory:&dir]) {
        [[NSFileManager defaultManager]createDirectoryAtPath:DirPath withIntermediateDirectories:YES attributes:nil error:nil];
    }
    return DirPath;
}

inline NSString * faceFilePath(NSString * fileName){
    return [faceFileDir() stringByAppendingPathComponent:fileName];
}


inline NSString * imageFilePath(NSString * fileName){
    return [imageFileDir() stringByAppendingPathComponent:fileName];
}
typedef void (^faceIdentifyMessageBlock)(CGRect rect,NSString * filePath);
typedef void (^requestAVAuthorizationStatusBlock)(AVAuthorizationStatus status);

#include "face_recognition.hpp"
@interface FaceDetectPlugin : CDVPlugin {
    
}
- (void)startPreview:(CDVInvokedUrlCommand*)command;

- (void)removeViews:(CDVInvokedUrlCommand *)command;

@end


@interface LNFaceCaremaView:UIView<AVCaptureVideoDataOutputSampleBufferDelegate>
{
    AVCaptureDevice * _captureDevice;
    AVCaptureOutput * _captureOutPut;
    AVCaptureInput * _catptureInput;
    AVCaptureSession * _captureSession;
    pthread_cond_t _cond;
    pthread_mutex_t _mutex;
    NSMutableArray * _images;
    NSMutableArray * _rects;
    dispatch_queue_t _queue;
    faceIdentifyMessageBlock _block;
   
    ScanningView * _lineView;
    AVCaptureVideoPreviewLayer * _previewLayer;
}

@property (nonatomic,copy)  requestAVAuthorizationStatusBlock authorizationsBlock;

- (void)startScan:(faceIdentifyMessageBlock)block;

- (void)stopScanFaceIdentify;



@end

@implementation LNFaceCaremaView

- (void)loadCompoents{
    
    if (_images==nil) {
        _images = [NSMutableArray new];
    }else{
        [_images removeAllObjects];
    }
    if (_rects==nil) {
        _rects = [NSMutableArray new];
    }else{
        [_rects removeAllObjects];
    }
    _captureDevice = [self cameraWithPosition:AVCaptureDevicePositionBack];
    _catptureInput = [AVCaptureDeviceInput deviceInputWithDevice:_captureDevice error:nil];
    _captureOutPut = [AVCaptureVideoDataOutput new];
    [((AVCaptureVideoDataOutput *)_captureOutPut) setSampleBufferDelegate:self queue:dispatch_get_main_queue()];
    // 设置视频格式
    [((AVCaptureVideoDataOutput *)_captureOutPut) setVideoSettings:[NSDictionary dictionaryWithObject:[NSNumber numberWithInt:kCVPixelFormatType_32BGRA] forKey:(id)kCVPixelBufferPixelFormatTypeKey]];
    _captureSession = [AVCaptureSession new];
    if([_captureSession canSetSessionPreset:AVCaptureSessionPresetMedium]){
        [_captureSession setSessionPreset:AVCaptureSessionPresetMedium];
    }
    if ([_captureSession canAddInput:_catptureInput]) {
        [_captureSession addInput:_catptureInput];
    }
    if([_captureSession canAddOutput:_captureOutPut]){
        [_captureSession addOutput:_captureOutPut];
    }
    AVCaptureConnection *captureConnection = [_captureOutPut connectionWithMediaType:AVMediaTypeVideo];
    captureConnection.videoOrientation = AVCaptureVideoOrientationPortrait;
    AVCaptureVideoPreviewLayer * previewLayer = [[AVCaptureVideoPreviewLayer alloc]initWithSession:_captureSession];
    previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill;
    [self.layer addSublayer:previewLayer];
    _previewLayer = previewLayer;
    previewLayer.frame = self.frame;
    

}

- (void)removeLineView{
    [_lineView removeFromSuperview];
}

- (void)addLineView{
    _lineView = [ScanningView new];
    [self addSubview:_lineView];
    CGFloat width = self.frame.size.height * 0.7;
    _lineView.frame = CGRectMake((self.frame.size.width - width) * 0.5 , (self.frame.size.height -width) * 0.5, width, width);
    _lineView.backgroundAlpha = 0;
    _lineView.cornerColor = [UIColor whiteColor];
    
}

- (void)captureOutput:(AVCaptureOutput *)output didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer fromConnection:(AVCaptureConnection *)connection
{
    UIImage * image = [self imageFromSampleBuffer:sampleBuffer];
    UIImage * souceImage = [self clicpImageFromRect:self.frame sourceImage:image];
    [self saveImage:souceImage];
}
- (void)startScanFace{
    // 删除上一次遗留的图片数据
    [[NSFileManager defaultManager]removeItemAtPath:imageFileDir() error:nil];
    [[NSFileManager defaultManager]removeItemAtPath:faceFileDir() error:nil];
    dispatch_async(dispatch_get_main_queue(), ^{
        [self addLineView];
    });
    _queue = dispatch_queue_create("async_", DISPATCH_QUEUE_CONCURRENT);
    pthread_cond_init(&_cond, nullptr);
    pthread_mutex_init(&_mutex, nullptr);
    dispatch_async(_queue, ^{
        struct timespec t;
        t.tv_sec = 1;
        while(pthread_cond_timedwait(&_cond, &_mutex, &t)){
            if (_images.count==0) {
                continue;
            }
            const char * path = [faceFilePath(_images.firstObject) UTF8String];
            std::string fileName = std::string(path);
            Rect rect;
            try{
               rect = detectFaces(fileName);
            }catch(NSException * e){
                LFLog(@"image == %@",[UIImage imageWithContentsOfFile:_images.firstObject]);
            }
            CGRect frame = CGRectMake(rect.left,rect.top,rect.right-rect.left, rect.bottom-rect.top);
            NSValue * value1 = [NSValue valueWithCGRect:frame];
            [_rects addObject:value1];
            if (CGRectEqualToRect(CGRectZero, frame)) {
                LFLog(@"not face found");
                //删除图片
                [[NSFileManager defaultManager]removeItemAtPath:[NSString stringWithFormat:@"%s",path] error:nil];
            }else if([self checkValid]){
                [_captureSession stopRunning];
                if (_block) {
                    _block(frame,imageFilePath(_images.firstObject));
                }
            }else{
                NSLog(@"%@",NSStringFromCGRect(frame));
            }
            if (_images.count) {
                [_images removeObjectAtIndex:0];
            }
        }
    });
    [_captureSession startRunning];
}
- (void)stopScanFaceIdentify{
    dispatch_async(dispatch_get_main_queue(), ^{
        [_captureSession stopRunning];
        [_previewLayer removeFromSuperlayer];
        [self removeLineView];
        [self removeFromSuperview];
        pthread_mutex_destroy(&_mutex);
        pthread_cond_destroy(&_cond);
    });
}

- (void)_loadCompoents{
    [self loadCompoents];
}

- (void)startScan:(faceIdentifyMessageBlock)block{
    _block = block;
    [AVCaptureDevice requestAccessForMediaType:AVMediaTypeVideo completionHandler:^(BOOL granted) {
        if (granted) {
            [self startScanFace];
        }else{
            AVAuthorizationStatus status = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeVideo];
            if (status==AVAuthorizationStatusDenied) {
                [self removeLineView];
                [_captureSession stopRunning];
                if (_authorizationsBlock) {
                    _authorizationsBlock(AVAuthorizationStatusDenied);
                }
            }else if(status==AVAuthorizationStatusNotDetermined){
                
            }
        }
    }];
}
- (BOOL)checkValid{
    if (_rects.count<MAXCOUT) {
        return NO;
    }
    __block CGRect rect1;
    __block CGRect rect2;
    __block BOOL flag = YES;
    NSInteger length = _rects.count;
    for (NSInteger idx = 0; idx<MAXCOUT; idx++) {
        NSValue * obj = _rects[length - MAXCOUT + idx];
        if (idx==0) {
            rect1 =  [obj CGRectValue];
        }else{
            rect2 = [obj CGRectValue];
            flag = (flag && CGRectEqualToRect(rect1, rect2));
        }
    }
    [_rects removeAllObjects];
    return flag;
}

- (AVCaptureDevice *)cameraWithPosition:(AVCaptureDevicePosition)position{
    NSArray *devices = [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo];
    for ( AVCaptureDevice *device in devices )
        if ( device.position == position ){
            return device;
        }
    return nil;
}
- (instancetype)initWithFrame:(CGRect)frame{
    if (self = [super initWithFrame:frame]) {
        [self _loadCompoents];
    }
    return self;
}

- (UIImage *)clicpImageFromRect:(CGRect)rect sourceImage:(UIImage *)image{
    CGFloat (^rad)(CGFloat) = ^CGFloat(CGFloat deg) {
        return deg / 180.0f * (CGFloat) M_PI;
    };
    // determine the orientation of the image and apply a transformation to the crop rectangle to shift it to the correct position
    CGAffineTransform rectTransform;
    switch (image.imageOrientation) {
        case UIImageOrientationLeft:
            rectTransform = CGAffineTransformTranslate(CGAffineTransformMakeRotation(rad(90)), 0, -image.size.height);
            break;
        case UIImageOrientationRight:
            rectTransform = CGAffineTransformTranslate(CGAffineTransformMakeRotation(rad(-90)), -image.size.width, 0);
            break;
        case UIImageOrientationDown:
            rectTransform = CGAffineTransformTranslate(CGAffineTransformMakeRotation(rad(-180)), -image.size.width, -image.size.height);
            break;
        default:
            rectTransform = CGAffineTransformIdentity;
    };
    
    // adjust the transformation scale based on the image scale
    rectTransform = CGAffineTransformScale(rectTransform, image.scale, image.scale);
    
    // apply the transformation to the rect to create a new, shifted rect
    CGRect transformedCropSquare = CGRectApplyAffineTransform(rect, rectTransform);
    // use the rect to crop the image
    CGImageRef imageRef = CGImageCreateWithImageInRect(image.CGImage, transformedCropSquare);
    // create a new UIImage and set the scale and orientation appropriately
    UIImage *result = [UIImage imageWithCGImage:imageRef scale:image.scale orientation:image.imageOrientation];
    // memory cleanup
    CGImageRelease(imageRef);
    
    return result;
}

- (void)saveImage:(UIImage *)image{
    // 图片保存
    if (image==nil) {
        LFLog(@"create image filed");
        return;
    }
    LFLog(@"image ==%@",image);
    NSString * fileName = [NSString stringWithFormat:@"%@%@",@((long)[[NSDate date]timeIntervalSinceReferenceDate]).stringValue,@".jpg"];
    NSString *filePath = imageFilePath(fileName);
    LFLog(@"filepath == %@",filePath);
    NSError * error1;
    BOOL result = [UIImageJPEGRepresentation(image,1) writeToFile:filePath options:NSAtomicWrite error:&error1];
    
    CGRect clicpRect = CGRectMake((LFSCREEN_WIDTH - self.frame.size.height)*0.5, (LFSCREEN_WIDTH - self.frame.size.height)*0.5, self.frame.size.height, self.frame.size.height);
    
    UIImage * faceImage = [self clicpImageFromRect:clicpRect sourceImage:image];
    NSString * clipFaceIconPath = faceFilePath(fileName);
    NSError * error2;
    BOOL result1 = [UIImageJPEGRepresentation(faceImage,1) writeToFile:clipFaceIconPath options:NSAtomicWrite error:&error2];
    if (error1||error2) {
        return;
    }
    // 保存成功会返回YES
    if (result&&result1){
        LFLog(@"保存成功");
        [_images addObject:fileName];
        pthread_cond_signal(&_cond);
    }
}
- (UIImage *) imageFromSampleBuffer:(CMSampleBufferRef) sampleBuffer {
    // 为媒体数据设置一个CMSampleBuffer的Core Video图像缓存对象
    CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
    // 锁定pixel buffer的基地址
    CVPixelBufferLockBaseAddress(imageBuffer, 0);
    
    // 得到pixel buffer的基地址
    void *baseAddress = CVPixelBufferGetBaseAddress(imageBuffer);
    
    // 得到pixel buffer的行字节数
    size_t bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer);
    // 得到pixel buffer的宽和高
    size_t width = CVPixelBufferGetWidth(imageBuffer);
    size_t height = CVPixelBufferGetHeight(imageBuffer);
    
    // 创建一个依赖于设备的RGB颜色空间
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    
    // 用抽样缓存的数据创建一个位图格式的图形上下文（graphics context）对象
    CGContextRef context = CGBitmapContextCreate(baseAddress, width, height, 8,
                                                 bytesPerRow, colorSpace, kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst);
    // 根据这个位图context中的像素数据创建一个Quartz image对象
    CGImageRef quartzImage = CGBitmapContextCreateImage(context);
    // 解锁pixel buffer
    CVPixelBufferUnlockBaseAddress(imageBuffer,0);
    
    // 释放context和颜色空间
    CGContextRelease(context);
    CGColorSpaceRelease(colorSpace);
    
    // 用Quartz image创建一个UIImage对象image
    UIImage *image = [UIImage imageWithCGImage:quartzImage];
    
    // 释放Quartz image对象
    CGImageRelease(quartzImage);
    
    return (image);
}

- (void)dealloc{
    LFLog(@"LNFaceCaremaView dealloc");
}

@end


@implementation FaceDetectPlugin
{
    LNFaceCaremaView * _caremaView;
}
- (void)startPreview:(CDVInvokedUrlCommand*)command{
    NSArray * arguments = command.arguments;

    CGFloat height = LFSCREEN_WIDTH * 0.75;
    if (arguments.count!=0) {
        height = LFSCREEN_WIDTH * [arguments.firstObject floatValue];
    }
    LNFaceCaremaView * caremaView;
    if (caremaView==nil) {
        caremaView = [[LNFaceCaremaView alloc]initWithFrame:CGRectMake(0, 0,LFSCREEN_WIDTH, height)];
        [self.webView.scrollView addSubview:caremaView];
        [self.webView insertSubview:caremaView belowSubview:self.webView.scrollView];
        self.webView.backgroundColor = [UIColor clearColor];
        self.webView.opaque = NO;
    }
    caremaView.authorizationsBlock = ^(AVAuthorizationStatus status) {
        [self authorizationStatusDeniedController];
    };
    [caremaView startScan:^(CGRect rect, NSString *filePath) {
        CDVPluginResult * result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:filePath];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
    }];
    _caremaView = caremaView;
}
- (void)removeViews:(CDVInvokedUrlCommand *)command{
    [_caremaView stopScanFaceIdentify];
}
- (void)requestAuthority{
    
}

- (void )authorizationStatusDeniedController{
    
    //
    UIAlertController * alertController = [UIAlertController alertControllerWithTitle:@"提示" message:@"请到手机设置页面打开相机权限" preferredStyle:UIAlertControllerStyleAlert];
    UIAlertAction * configureAction = [UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
        [[UIApplication sharedApplication]openURL:[NSURL URLWithString:UIApplicationOpenSettingsURLString]];
    }];
    UIAlertAction * cancelAction = [UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil];
    [alertController addAction:configureAction];
    [alertController addAction:cancelAction];
    [self.viewController presentViewController:alertController animated:YES completion:nil];
    
}

- (void)drawRect:(CGRect)rect
{
    
}
@end


