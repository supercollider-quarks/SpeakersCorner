SpatioScope {

	classvar <>group;

	var <locations, <server,  <bounds, <>background, <>foreground;
	var <numChannels;
	var <proxy, <resp, <skipjack, <isOn, <amps;
	var <parent, <parentView, <topZone, <startBtn, <stopBtn, <magSlider;
	var <ampViews, <>ampAlpha = 1.0, <>magnify=1, <>redLevel=1.0;
	var <redCol, <getAmpsFunc;
	var <>clickAction, <defaultMouseDownAction;

	var <rate;

	*new { arg locations, server, parent, bounds, busOffset = 0;
		locations = locations ?? { SpatioScope.gridPos(2, 2) };
		server = server ? Server.default;

		^super.newCopyArgs(locations, server)
			.init(bounds, busOffset)
			.gui(parent)
			.start
	}

	init { |argBounds, argOffset|
		bounds = argBounds ?? { Rect(0,0,410,410) };
		numChannels = locations.size;

		proxy = proxy ?? {  NodeProxy.control(server, this.numChannels) };
		group !? { proxy.parentGroup_(group) };
		proxy.set(\busOffset, argOffset ? 0);

		this.arListen;

		rate = \audio;
		isOn = false;

		resp.remove;

		if (server.isLocal) {
			getAmpsFunc = { this.amps_(this.getAmpValues) }
		} {
			getAmpsFunc = { server.listSendMsg([\c_getn, proxy.index, this.numChannels]) };

			resp = OSCFunc({ arg msg;
				var amps;
				// check if this reply message is for this spatioscope
				if ( msg[[1, 2]] == [proxy.bus.index, this.numChannels] ){
					amps = msg.drop(3);
					// "got bus values with % values: %\n".postf(amps.size, amps);
					{  this.amps_(amps); }.defer;
				};
			}, '/c_setn', server.addr).permanent_(true);
		};

		skipjack = SkipJack(
			{ proxy.wakeUp; this.updateViews; },
			0.1,
			{ parent.isClosed; },
			this.class.name,
			autostart: false
		);

		defaultMouseDownAction = { |view, x, y, mod|
			var indices = this.indicesFor(x, y);
			clickAction.value(indices, x, y, mod);
		};

		clickAction = { |indices, x, y, mod|
			"clicked in ampView(s) at % at pos x: % y: % with mod key: %\n"
			.postf(indices, x, y, mod)
		};
	}

	maxBusNum {
		^if ( rate == \audio ){
			server.options.numAudioBusChannels
		} {
			server.options.numControlBusChannels
		} - this.numChannels;
	}

	busOffset { ^proxy.get(\busOffset) }

	busOffset_ { |inChan=0|
		if (inChan.inclusivelyBetween(0, this.maxBusNum ) ) {
			proxy.set(\busOffset, inChan);
		}{
			"%: new busOffset out of range of valid bus numbers!".format(thisMethod).warn;
		};
	}

	set { |...args|
		proxy.set(*args);
	}
	get { |...args|
		proxy.get(*args);
	}

	gui { |argParent|
		var butWidth = 38;

		background = background ?? { Color(0, 0, 0.15) }; // dark blue
		foreground = foreground ?? { Color(0.5, 0.5, 1.0) }; // light blue

		parent = argParent ?? {
			Window(this.class.name, bounds.moveBy(200, 200).resizeBy(10, 30)).front;
		};
		parentView = parent.asView;
		parentView.background_(background);

		topZone = CompositeView(parent, Rect(0,0, bounds.width, 30));
		topZone.addFlowLayout;

		#startBtn, stopBtn = [ \start, \stop ].collect { |name, i|
			Button(topZone, Rect(i * (butWidth + 2) + 2, 2, butWidth, 20))
			.states_([
				[name, Color.white, Color.clear],
				[name, Color.white, Color.blue(1.0)]
			])
			.action_({ this.perform(name); });
		};

		magSlider = EZSlider(topZone,
			(bounds.width - (butWidth * 2) - 20) @ 20,
			\magnify,
			[1, 100, \exp],
			{ |sl| magnify = sl.value }, magnify,
			labelWidth: 45, numberWidth: 30);
		magSlider.labelView.stringColor_(foreground);
		magSlider.numberView.background_(foreground);
		//		magSlider.numberView.resize_(3);
		//		magSlider.sliderView.resize_(2);

		this.showLocs;
		this.stop.start;
	}

	show { |start = true|
		if (parent.isClosed) { this.gui } { parent.front };
		if (start) { this.start };
	}
	hide { |stop = true|
		if (parent.isClosed.not) { parent.minimize };
		if (stop) { this.stop };
	}

	showLocs {
		var center = bounds.center;
		var size = bounds.center.x * 0.1;
		parentView.mouseDownAction = defaultMouseDownAction;

		redCol = Color(1, 0.6, 0, ampAlpha);

		ampViews = locations.collect { |point, i|
			var left = point.x + 1 * center.x;
			var top = point.y + 1.05 * center.y;
			StaticText(parentView, Rect.aboutPoint(left@top, size, size))
			.string_((i + 1).asString).align_(\center)
			.stringColor_(foreground)
			.background_( Color.black.alpha_(ampAlpha) );
		};
	}

	*ringPos { |num=6, radius=0.7, angleOffset=0|
		var angles = { |i| (2pi * (i / num)) + angleOffset }.dup(num);
		^angles.collect { |angle| Polar(radius, angle).asPoint };
	}

	*ring { |num=6, radius=0.7, angleOffset=0, server, parent, bounds|
		var locs = this.ringPos(num, radius, angleOffset);
		^this.new(locs, server, parent, bounds);
	}


	*rings { |nums, radii, angleOffsets = 0, server, parent, bounds|
		var locs;
		var numrings = nums.size;
			// earlier circles are outer
			// (assumes dome shape, low channels on bottom, as in IEM CUBE)
		radii = radii ?? { (numrings .. 1) / (numrings + 1) };
		angleOffsets = angleOffsets ?? 0;
		locs = [ nums, radii, angleOffsets ].flop.collect { |list| this.ringPos(*list) }.flat;

		^this.new(locs, server, parent, bounds);
	}

	// grid speaker positions
	*gridPos { |numx = 2, numy = 6|

		^Array.series( numy, 1/(2*numy), 1/numy ).collect{ |y|
			Array.series( numx, 1/(2*numx), 1/numx ).collect{ |x|
				Point(x*2-1,y*2-1);
			}
		}.flatten;
	}

	*grid { |numx=2, numy=6, server, parent, bounds|
		var locs = this.gridPos(numx, numy);
		^this.new(locs, server, parent, bounds);
	}

	/* convert decayTime to samplerate:
	var decayTime = 3;
	var samplerate = 44100;
	var numSamplesForRT60 = (samplerate * decayTime);
	var coeff = (0.001 ** (1/numSamplesForRT60)).postln;
	coeff ** samplerate; // must be decayTime again
	*/

	// listen to control buses
	krListen { |busIndex, numChannels, decayTime|
		rate = \control;
		busIndex !? { proxy.set(\busOffset, busIndex) };
		numChannels = numChannels ? this.numChannels;
		decayTime !? { proxy.set(\decayTime, decayTime) };

		proxy.source = { |decayTime = 1.0, busOffset|
			var numSamplesForRT60 = SampleRate.ir * decayTime;
			var coeff = 0.001 ** numSamplesForRT60.reciprocal;
			PeakFollower.ar(In.kr(busOffset, numChannels), coeff)
		};
	}

	// listen to audio rate buses
	arListen { |busIndex, numChannels, decayTime|
		rate = \audio;
		busIndex !? { proxy.set(\busOffset, busIndex) };
		numChannels = numChannels ? this.numChannels;
		decayTime !? { proxy.set(\decayTime, decayTime) };

		proxy.source = { |decayTime = 1.0, busOffset|
			var numSamplesForRT60 = SampleRate.ir * decayTime;
			var coeff = 0.001 ** numSamplesForRT60.reciprocal;
			A2K.kr(PeakFollower.ar(InFeedback.ar(busOffset, numChannels), coeff))
		};
	}

	start {
		if(server.serverRunning.not) {
			"SpatioScope: server not running.".warn;
			this.stop;
			^this
		};

		isOn = true;
		skipjack.start;
		resp.remove.add;
	}

	getAmpValues {
		if (server.serverRunning) {
			^proxy.bus.getnSynchronous
		} {
			^[0]
		}
	}

	updateViews {
		var isOnValue = isOn.binaryValue;
		if (parent.isClosed.not) {
			startBtn.value_(isOnValue);
			stopBtn.value_(1 - isOnValue);
			magSlider.value_(magnify);
			getAmpsFunc.value;
		};
	}

	stop {
		skipjack.stop;
		proxy.free;
		resp.remove;
		isOn = false;
		this.updateViews;
		this.amps_([]);
	}

	amps_ { arg vals;
		var amp, col;
		// "amps coming in: %\n".postf(vals);
		amps = vals;
		if (parent.isClosed.not) {
			defer {
				ampViews.do { |el, i|
					amp = (vals[i] ? 0 * magnify).sqrt;
					col = if (amp > redLevel, redCol, { Color.green( amp, ampAlpha ) });
					el.background_(col)
				}
			}
		};
	}

	indicesFor { |x, y|
		^ampViews.selectIndices { |vw| vw.bounds.contains(x@y) }
	}
}
