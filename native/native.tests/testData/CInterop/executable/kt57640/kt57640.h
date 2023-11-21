#import <Foundation/NSObject.h>

@interface Base : NSObject
@property (readwrite) Base* delegate;
@end

@protocol Foo
@property (readwrite) id<Foo> delegate;
@end

@protocol Bar
@property (readwrite) id<Bar> delegate;
@end

@interface Derived : Base<Bar, Foo>
// This interface does not have re-declaration of property `delegate`.
// Return type of getter `delegate()` and param type of setter `setDelegate()` are:
//   the type of property defined in the first mentioned protocol (id<Bar>), which is incompatible with property type.
@end

@interface DerivedWithPropertyOverride : Base<Bar, Foo>
// This interface does not have re-declaration of property `delegate`.
// Return type of getter `delegate()` and param type of setter `setDelegate()` are `DerivedWithPropertyOverride*`
@property (readwrite) DerivedWithPropertyOverride* delegate;
@end
