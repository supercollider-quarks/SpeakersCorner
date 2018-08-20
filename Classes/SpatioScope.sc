SpatioScope {

	var <locations, <server,  <bounds, <>background, <>foreground;
	var <numChannels, <offset = 0;
	var <proxy, <resp, <skipjack, <isOn, <lastAmps;
	var <parent, <parentView, <topZone, <startBtn, <stopBtn, <magSlider;
	var <ampViews, <>ampAlpha = 0.6, <>magnify=1, <>redLevel=0.95;
	var <redCol;
	var <>clickAction, <defaultMouseDownAction;

	var <rate;

	*new { arg locations, server, parent, bounds;
		locations = locations ?? { SpatioScope.gridPos(2, 2) };
		server = server ? Server.default;

		^super.newCopyArgs(locations, server)
			.init(bounds)
			.gui(parent)
			// .start
	}

	init { |argBounds|
		bounds = argBounds ?? { Rect(0,0,410,410) };
		numChannels = locations.size;

		proxy = proxy ?? {  NodeProxy.control(server, this.numChannels) };
		proxy.prime({
			A2K.kr(PeakFollower.kr(InFeedback.ar(this.offset, this.numChannels), 0.9999))
		});

		rate = \audio;
		isOn = false;

		resp.remove;
		resp = OSCFunc({ arg msg;
			var amps;
			// check if this reply message is for this spatioscope
			if ( msg[[1, 2]] == [proxy.bus.index, this.numChannels] ){
				lastAmps = msg.drop(3);
				// "got bus values with % values: %\n".postf(lastAmps.size, lastAmps);
				{  this.amps_(lastAmps * (magnify ? 1)); }.defer;
			};
		}, '/c_setn', server.addr).permanent_(true);

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
			"clicked in ampView(s) at % at pos x: % y: % with mod key: %\n".postf(indices, x, y, mod)
		};
	}

	maxBusNum {
		^if ( rate == \audio ){
			server.options.numAudioBusChannels
		} {
			server.options.numControlBusChannels
		} - this.numChannels;
	}

	offset_ { |inChan=0|
		if (inChan.inclusivelyBetween(0, this.maxBusNum ) ) {
			offset = inChan;
			proxy.rebuild;
			if (skipjack.task.isPlaying) { this.stop.start };
		}{
			"%: new offset out of range of valid busnumbers!".format(thisMethod).warn;
		};
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

		redCol = Color.red(1, ampAlpha);

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


	// listen to control buses
	krListen {
		rate = \control;
		proxy.source = {
			Amplitude.kr(In.kr(this.offset, this.numChannels), 0, 0.5)
		};
	}

	// listen to audio rate buses
	arListen {
		rate = \audio;
		proxy.source = {
			Amplitude.kr(InFeedback.ar(this.offset, this.numChannels), 0, 0.5)
		};
	}

	start {
		if(server.serverRunning.not) {
			"SpatioScope: server not running.".warn;
			this.stop;
			^this
		};

		isOn = true;

		fork ({
			proxy.rebuild;
			server.sync;
			0.4.wait;
			skipjack.start;
			resp.remove.add;
			this.updateViews;
		}, AppClock);
	}

	updateViews {
		var isOnValue = isOn.binaryValue;
		if (parent.isClosed.not) {
			startBtn.value_(isOnValue);
			stopBtn.value_(1 - isOnValue);
			magSlider.value_(magnify);
			server.listSendMsg([\c_getn, proxy.index, this.numChannels]);
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
		if (parent.isClosed.not) {
			ampViews.do { |el, i|
				amp = (vals[i] ? 0).sqrt;
				col = if (amp > redLevel, redCol, { Color.yellow( amp, ampAlpha ) });
				el.background_(col)
			}
		};
	}

	indicesFor { |x, y|
		^ampViews.selectIndices { |vw| vw.bounds.contains(x@y) }
	}
}
