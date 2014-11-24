package com.me.mygdxgame;

import aurelienribon.tweenengine.TweenAccessor;

public class TileAccessor implements TweenAccessor<Tile> {
	public static final int TWEEN_X = 0, TWEEN_Y = 1, TWEEN_SIZE = 2;

	@Override public int getValues(Tile target, int tweenType, float[] returnValues) {
		switch(tweenType) {
			case TWEEN_X: returnValues[0] = target.getX(); break;
			case TWEEN_Y: returnValues[0] = target.getY(); break;
			case TWEEN_SIZE: returnValues[0] = target.getSize(); break;
		}
		return 1;
	}

	@Override public void setValues(Tile target, int tweenType, float[] newValues) {
		switch(tweenType) {
			case TWEEN_X: target.setX(newValues[0]); break;
			case TWEEN_Y: target.setY(newValues[0]); break;
			case TWEEN_SIZE: target.setSize(newValues[0]); break;
		}
	}
}
