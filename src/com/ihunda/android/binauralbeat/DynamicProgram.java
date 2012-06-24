package com.ihunda.android.binauralbeat;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

import android.bluetooth.BluetoothAdapter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.neurosky.thinkgear.TGDevice;

public class DynamicProgram extends Program {
	TGDevice tgDevice;
	BluetoothAdapter btAdapter;

	public class Measurement extends Handler {
		private int currentAttention;
		private int currentMeditation;

		public Iterator<Float> getAttentionIterator() {
			return new Iterator<Float>() {
				@Override
				public boolean hasNext() {
					return true;
				}

				@Override
				public Float next() {
					return (float) (currentAttention / 100.0);
				}

				@Override
				public void remove() {
				}
			};

		}

		public Iterator<Float> getMeditationIterator() {
			return new Iterator<Float>() {
				@Override
				public boolean hasNext() {
					return true;
				}

				@Override
				public Float next() {
					return (float) (currentMeditation / 100.0);
				}

				@Override
				public void remove() {
				}
			};

		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case TGDevice.MSG_STATE_CHANGE:
				switch (msg.arg1) {
				case TGDevice.STATE_IDLE:
					break;
				case TGDevice.STATE_CONNECTING:
					break;
				case TGDevice.STATE_CONNECTED:
					tgDevice.start();
					break;
				case TGDevice.STATE_DISCONNECTED:
					break;
				case TGDevice.STATE_NOT_FOUND:
				case TGDevice.STATE_NOT_PAIRED:
				default:
					break;
				}
				break;
			case TGDevice.MSG_POOR_SIGNAL:
				Log.v("HelloEEG", "PoorSignal: " + msg.arg1);
			case TGDevice.MSG_ATTENTION:
				Log.v("HelloEEG", "Attention: " + msg.arg1);
				currentAttention = msg.arg1;
				break;
			case TGDevice.MSG_MEDITATION:
				Log.v("HelloEEG", "Meditation: " + msg.arg1);
				currentMeditation = msg.arg1;
				break;
			case TGDevice.MSG_RAW_DATA:
				int rawValue = msg.arg1;
				break;
			default:
				break;
			}
		}
	}

	private static class SlidingWindowIterator implements Iterator<Float> {
		private final Iterator<Float> innerIterator;
		private Queue<Float> slidingWindow;
		private int maxSize;

		public SlidingWindowIterator(Iterator<Float> innerIterator, int maxSize) {
			super();
			this.innerIterator = innerIterator;
			this.slidingWindow = new ArrayDeque<Float>(maxSize);
			this.maxSize = maxSize;
		}

		public boolean hasNext() {
			return innerIterator.hasNext();
		}

		public Float next() {
			slidingWindow.offer(innerIterator.next());
			if (slidingWindow.size() > maxSize) {
				slidingWindow.poll();
			}
			float sum = 0f;
			for (float value : slidingWindow) {
				sum += value;
			}
			return sum / slidingWindow.size();
		}

		public void remove() {
			innerIterator.remove();
		}
	}

	private Measurement measurement;

	public DynamicProgram(String name) {
		super(name);
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		this.measurement = new Measurement();
		if (btAdapter != null) {
			tgDevice = new TGDevice(btAdapter, measurement);
		}
		tgDevice.connect(false);
	}

	@Override
	protected Iterator<Period> getPeriodsIterator() {

		return new DynamicPeriodsIterator(new SlidingWindowIterator(
				measurement.getAttentionIterator(), 10),
				new SlidingWindowIterator(measurement.getMeditationIterator(),
						10));
	}

	private static final class DynamicPeriodsIterator implements
			Iterator<Period> {
		private final Iterator<Float> attentionIterator;
		private Iterator<Float> meditationIterator;

		public DynamicPeriodsIterator(Iterator<Float> attentionIterator,
				Iterator<Float> meditationIterator) {
			super();
			this.attentionIterator = attentionIterator;
			this.meditationIterator = meditationIterator;
			visualization = new EegVisualization(attentionIterator);
		}

		private final EegVisualization visualization;

		@Override
		public boolean hasNext() {
			return attentionIterator.hasNext() && meditationIterator.hasNext();
		}

		@Override
		public Period next() {
			float attention = attentionIterator.next();
			float meditation = meditationIterator.next();
			float pitch = 400 - meditation * 200f;
			float beatFrequency = 5.9f;
			Period newPeriod = new Period(1, SoundLoop.NONE, 0.4f, null)
					.addVoice(
							new BinauralBeatVoice(beatFrequency, beatFrequency, 1.0f,
									pitch)).addVoice(new BinauralBeatVoice(beatFrequency, beatFrequency, attention/3.0f,
									pitch*5.0f/4.0f))
									.addVoice(new BinauralBeatVoice(beatFrequency, beatFrequency, attention/3.0f,
									pitch*3.0f/2.0f))
									.setV(
							visualization);
			return newPeriod;
		}

		@Override
		public void remove() {
		}

	}

	public static class EegVisualization implements CanvasVisualization {

		private static final int MAX_SIZE = 25;
		Queue<Float> slidingWindow;
		private final Iterator<Float> measurement;

		public EegVisualization(Iterator<Float> measurement) {
			super();
			this.measurement = measurement;
			this.slidingWindow = new ArrayDeque<Float>(MAX_SIZE);
		}

		@Override
		public void setFrequency(float beat_frequency) {

		}

		@Override
		public void redraw(Canvas c, int width, int height, float now,
				float totalTime) {
			Float nextValue = measurement.next();
			slidingWindow.offer(nextValue);
			if (slidingWindow.size() > MAX_SIZE) {
				slidingWindow.poll();
			}
			float[] values = new float[slidingWindow.size() * 4];
			int x = 0;
			int yOffset = 200;
			int yScaling = 40;
			float lastValue = 0f;
			for (Float v : slidingWindow) {
				values[x * 4] = x * 10 + 100;
				values[x * 4 + 1] = yOffset - lastValue * yScaling;
				values[x * 4 + 2] = x * 10 + 110;
				values[x * 4 + 3] = yOffset - v * yScaling;
				lastValue = v;
				x++;
			}
			Paint paint = new Paint();
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(Color.argb(100, 255, 255, 255));
			c.drawColor(Color.rgb(0, 0, 0));
			c.drawLines(values, paint);
		}

	}

}
