/*
// Two use common cases:

// 1. measure only hardware latency, i.e. electrically:
// connect first hardware output to first hardware input

// set hardwareBufferSize to as low as it will go
s.options.hardwareBufferSize = 128; s.reboot;

// test with one channel output, pulse every 0.5 sec,
Latency.testAudio(1, 0.5); // test 1 chan
Latency.stop;
Latency.latencies;

// 2. measure multiple channels over loudspeakers and mic:
// set up loudspeaker as intended
// put single mic in sweet spot

// set hardwareBufferSize to as low as it will go
s.options.hardwareBufferSize = 128; s.reboot;

// tune threshold until you get steady times for each channel
Latency.threshold =  0.1;
// when times are stable, stop and post:
Latency.stop;


// to debug, see all messages coming in
Latency.verbose = true;
// and compare measured latencies
Latency.latencies;
*/

Latency {
	classvar <syn, <resp, <latencies, <serv, <lastTime, <threshold = 0.03;
	classvar <>verbose = false, waitingForMic = false;

	*initClass {
		Class.initClassTree(OSCFunc);

		// register to receive latency values
		resp = OSCFunc({ arg msg;
			var which = msg[2].asInteger;
			var exactTimeInSamples = msg[3].asInteger;
			var deltaSamples, deltaTimeMsec;
			var lastInChan;

			if ( verbose ) { msg.postln; };
			// make sure we only listen to triggers from our synth
			if ( syn.notNil and: { msg[1] == syn.nodeID }){

				if (which > 0) {
					// store ID and time for internal pulse
					lastTime = [which, exactTimeInSamples];
					waitingForMic = true;
				} {
					// measured pulse trigger from input
					if (waitingForMic) {
						lastInChan = lastTime[0] - 1;
						deltaSamples = (exactTimeInSamples - lastTime[1]);
						deltaTimeMsec = (deltaSamples * 1000 / serv.sampleRate).round(0.01);

						[ lastTime[0], deltaSamples, deltaTimeMsec ].postln;
						waitingForMic = false;
						// store latencies for channels
						latencies[lastInChan] = deltaSamples;
					}
				}
			}
		}, '/tr');
	}

	*testAudio { |numChans=5, maxDT = 0.5, server, inChan=0|
		serv = server ? Server.default;
		latencies = Array.newClear(numChans);
		resp.enable;
		fork {
			syn = { |threshold = 0.02|
				var pulses, audIn, phase;
				var pulseFreq = (maxDT * 2 * numChans).reciprocal;

				phase = Phasor.ar(0, 1, 0, 2 ** 30);	// time in samples
				audIn = SoundIn.ar(inChan);				// mike input

				pulses =  Decay2.ar(
					Impulse.ar( pulseFreq,
						0.99 - ((0..numChans - 1) / numChans) // phase
					), 0.0, 0.002
				);
				// send when audioin triggers
				SendTrig.ar( Trig1.ar(audIn.abs > threshold, 0.05), 0, phase);
				// send when each output plays a trigger
				SendTrig.ar(pulses > 0.03, (1..numChans), phase);
				(pulses ++ [ Silent.ar, audIn ])
			}.play(serv);
			serv.sync;
			syn.set(\threshold, threshold);
			0.01.wait;
			"*** chan samples msec latency:".postln;
		}
	}

	*threshold_ { |val|
		if (val.isKindOf(SimpleNumber)) {
			threshold = val.clip(0.001, 1);
			syn !? { syn.set(\threshold, threshold) }
		};
	}

	*stop {
		resp.disable;
		syn.free;
		syn = nil;
		this.post;
	}

	*post {
		"// measured latencies:".postln;
		"in samples: ".post; latencies.postcs;
		"in seconds: ".post; (latencies / serv.sampleRate).round(0.00001).postcs;
		"in milliseconds: ".post; (latencies * 1000 / serv.sampleRate).round(0.01).postcs;
	}
}