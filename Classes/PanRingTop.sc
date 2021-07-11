PanRingTop : MultiOutUGen {
	*ar {|numChans=4, in, azi=0.0, elev=0.0, width = 2, orientation=0.5|
		^[numChans, in, azi, elev, width, orientation ].flop.collect({ |inputs|
			this.ar1(*inputs)
		}).unbubble;
	}
	*ar1 { |numChans=4, in, azi=0.0, elev=0.0, width = 2, orientation=0.5|
		var down, up; #down, up = Pan2.ar(in, (elev * 2 - 1).clip(-1, 1));
		"[numChans, down, azi, width, orientation]".postln;
		[numChans, down, azi, width, orientation].postln;

		^PanAz.ar(numChans, down, azi, 1, width, orientation) ++ [ up ];
	}
	*kr {|numChans=4, in, azi=0.0, elev=0.0, width = 2, orientation=0.5|
		^[numChans, in, azi, elev, 1, width, orientation ].flop.collect({ |inputs|
			this.kr1(*inputs)
		}).unbubble;
	}
	*kr1 { |numChans=4, in, azi=0.0, elev=0.0, width = 2, orientation=0.5|
		var down, up; #down, up = Pan2.kr(in, (elev * 2 - 1).clip(-1, 1));
		^PanAz.kr(numChans, down, azi, width, orientation) ++ [ up ];
	}
}
// 1 top and 1 bottom speaker,
// positions azi wraps, elev clips.
PanRingTopBot : MultiOutUGen {
	*ar {|numChans=4, in, azi=0.0, elev=0.0, width = 2, orientation=0.5|
		^[numChans, in, azi, elev, width, orientation ].flop.collect({ |inputs|
			this.ar1(*inputs)
		}).unbubble;
	}
	*ar1 { |numChans=4, in, azi=0.0, elev=0.0, width = 2, orientation=0.5|
		var down, ring, up;
		#down, ring, up = PanAz.ar(3, in, elev.clip(-1, 1) * 2/3, orientation: 1);
		^PanAz.ar(numChans, ring, azi, 1, width, orientation) ++ [up, down];
	}
	*kr {|numChans=4, in, azi=0.0, elev=0.0, width = 2, orientation=0.5|
		^[numChans, in, azi, elev, width, orientation ].flop.collect({ |inputs|
			this.kr1(*inputs)
		}).unbubble;
	}
	*kr1 { |numChans=4, in, azi=0.0, elev=0.0, width = 2, orientation=0.5|
		var down, ring, up;
		#down, ring, up = PanAz.kr(3, in, elev.clip(-1, 1) * 2/3, orientation: 1);
		^PanAz.kr(numChans, ring, azi, 1, width, orientation) ++ [up, down];
	}
}