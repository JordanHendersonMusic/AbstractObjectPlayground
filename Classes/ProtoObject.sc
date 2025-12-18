ProtoObject : ExperimentalAbstractObject {
	classvar defaultObject;
	var <protoObjectUnderlyingDictionary;

	*initClass {
		Class.initClassTree(IdentityDictionary);
		// Here are a bunch of methods that all ProtoObjects implement, you cannot delete their implementaion, but you can replace it!
		// They can all be overriden when the user constructs a ProtoObject.
		defaultObject = IdentityDictionary.newFrom([
			protoObjectLock: false, // Used to forbid adding new entries to the object.

			// These are the many printing methods the interpreter needs
			asString: {|self, limit = 512|
				var string = String.streamContentsLimit({ |stream|
					self.protoObjectUnderlyingDictionary.printItemsOn(stream)
				}, limit);
				"ProtoObject[" ++ string ++ "]"
			},
			postln: {|self| self.asString.postln },
			printOn: { |self, stream| stream << self.asString },
			dump: {|self| "ProtoObject" },
			storeOn: { |self, stream| self.asString.printOn(stream) },

			class: ProtoObject,

			// Yielding, routine support is assumed
			yield: {|self| self.sc_abstract_object_yield },
			alwaysYield: {|self| self.sc_abstract_object_always_yield	},
			yieldAndReset: {|self, reset| self.sc_abstract_object_yield_and_reset(reset) },
			idle: {|self, val|
				var time = thisThread.beats;
				while { thisThread.beats - time < val } { self.value.yield }
			},

			// Copying
			copy: { |self| self.shallowCopy },
			contentsCopy: { |self| self.shallowCopy },
			shallowCopy: { |self| self.sc_abstract_object_copy_shallow },
			copyImmutable: { |self| self.sc_abstract_object_copy_immutable },
			deepCopy: { |self| self.sc_abstract_object_copy_deep },

			// Value is provided by default
			value: {|self| self },

			// These two makes sense where as the other 'is*' methods don't as 'nil' is not considered a 'real' object.
			isNil: { false },
			notNil: { true },

			// Needed for try and protect return mechanism
			isException: { false },

			// ProtoObjects support equality
			hash: { |self| self.sc_abstract_object_hash },
			basicHash: { |self| self.sc_abstract_object_hash },
			'!=': { |self, other| (self == other).not },
			'==': { |self, other|
				if(other.sc_abstract_object_is_kind_of(ProtoObject)){
					self.protoObjectUnderlyingDictionary == other.protoObjectUnderlyingDictionary
				} {
					false
				}
			},
		]);
	}

	*new { |...args, kwargs|
		if(args.size != 0) { Error("ProtoObject only accepts keyword arguments").throw };
		forBy(0, kwargs.size - 1, 2)  { |i|
			if (kwargs[i] == \super) {
				if(kwargs[i + 1].sc_abstract_object_is_kind_of(ProtoObject).not){
					^Error("Attempting to add a non-ProtoObject as super is not allowed").throw
				}
			}
		};
		^super.newCopyArgs(IdentityDictionary.newFrom(kwargs).proto_(defaultObject))
	}

	// Finds message implementation by walking by hierachy
	sc_proto_object_walk_heirachy { |base, selector, args, kwargs, onFailure|
		var maybeSuper;
		var found = this.protoObjectUnderlyingDictionary[selector];
		^found !? {
			^found.performArgs(\value, [base] ++ args, kwargs)
		} ?? {
			maybeSuper = this.protoObjectUnderlyingDictionary[\super];
			maybeSuper !? {
				// do NOT remove the '^' here, the non-local return mechanism (used by exceptions and yield) is broken when used in combination with the extended and jump bytecodes
				^maybeSuper.sc_proto_object_walk_heirachy(base, selector, args, kwargs, onFailure)
			} ?? {
				// do NOT remove the '^' here, the non-local return mechanism (used by exceptions and yield) is broken when used in combination with the extended and jump bytecodes
				^onFailure.();
			}
		}
	}


	// The following 'perfom' methods ultimately reroute back through doesNotUnderstand, provide an implementation for this if you wish to override what it means to act on a message.
	performArgs { |selector, args, kwargs| ^this.sc_abstract_object_perform_args(selector, args, kwargs) }
	superPerformArgs { |selector, args, kwargs| ^this.sc_abstract_object_super_perform_args(selector, args, kwargs)  }
	performMsg { |msg| ^this.sc_abstract_object_perform_msg(msg) }
	perform { | selector ... args| ^this.sc_abstract_object_perform(*args) }
	performList { |...args, kwargs| ^this.performArgs(\sc_abstract_object_perform_list, args, kwargs) }


	// This message implements message dispatch.
	doesNotUnderstand { |selector ...args, kwargs|
		^if (selector.isSetter) {
			if (this.protoObjectLock) {
				^Error("% was locked and could not add message %".format(this.asString, selector)).throwOrPost
			} {
				if (selector == \super_) {
					if(args[0].sc_abstract_object_is_kind_of(ProtoObject).not){
						^Error("Attempting to add a non-ProtoObject as super is not allowed").throw
					}
				};
				this.protoObjectUnderlyingDictionary[selector.asGetter] = args[0];
				^this
			}
		} {
			^this.sc_proto_object_walk_heirachy(this, selector, args, kwargs, {
				^this.sc_proto_object_walk_heirachy(this, \doesNotUnderstand, [selector] ++ args, kwargs, {
					^DoesNotUnderstandError(this, selector, args, kwargs).throw
				})
			})
		}
	}
}
