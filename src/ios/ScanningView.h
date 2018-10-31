
#import <UIKit/UIKit.h>


@interface ScanningView : UIView

/** 边框颜色，默认白色 */
@property (nonatomic, strong) UIColor *borderColor;
/** 边角颜色，默认微信颜色 */
@property (nonatomic, strong) UIColor *cornerColor;
/** 边角宽度，默认 2.f */
@property (nonatomic, assign) CGFloat cornerWidth;
/** 扫描区周边颜色的 alpha 值，默认 0.2f */
@property (nonatomic, assign) CGFloat backgroundAlpha;


@end
