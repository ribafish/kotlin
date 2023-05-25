#import <Foundation/NSObject.h>

@interface InterfaceBase : NSObject
@property (readwrite) InterfaceBase* delegate;
@end

@protocol IntegerProperty
@property (readonly) NSInteger delegate;
@end

@protocol UIntegerProperty
@property (readonly) NSUInteger delegate;
@end

// Note: `clang -Werror` would raise error for InterfaceDerivedWithoutPropertyOverride:
// error: 'retain (or strong)' attribute on property 'delegate' does not match the property inherited from 'IntegerProperty' [-Wproperty-attribute-mismatch]
@interface InterfaceDerivedWithoutPropertyOverride : InterfaceBase<UIntegerProperty, IntegerProperty>
// property `delegate` is an intersection override from
// - `InterfaceBase* InterfaceBase.delegate` and
// - `NSUInteger IntegerProperty.delegate`
// - `NSInteger IntegerProperty.delegate`
// Clang chooses the following types for intersection override:
// - for field `delegate`: type of property in base class
// - for method `delegate()`: type of property in first mentioned protocol
@end

// Note: `clang -Werror` would raise error for InterfaceDerivedWithPropertyOverride:
// error: 'retain (or strong)' attribute on property 'delegate' does not match the property inherited from 'IntegerProperty' [-Wproperty-attribute-mismatch]
@interface InterfaceDerivedWithPropertyReadonlyOverride : InterfaceBase<UIntegerProperty, IntegerProperty>
// property `delegate` is affected by parent declarations:
@property (readonly) InterfaceDerivedWithPropertyReadonlyOverride* delegate;
@end

@protocol IntegerPropertyReadWrite
@property (readwrite) NSInteger delegate;
@end

@protocol UIntegerPropertyReadWrite
@property (readwrite) NSUInteger delegate;
@end

// Note: `clang -Werror` would raise error for InterfaceDerivedWithPropertyReadWriteOverride:
// error: 'retain (or strong)' attribute on property 'delegate' does not match the property inherited from 'IntegerProperty' [-Wproperty-attribute-mismatch]
@interface InterfaceDerivedWithPropertyReadWriteOverride : InterfaceBase<UIntegerPropertyReadWrite, IntegerPropertyReadWrite>
// property `delegate` is affected by parent declarations:
@property (readwrite) InterfaceDerivedWithPropertyReadWriteOverride* delegate;
@end
