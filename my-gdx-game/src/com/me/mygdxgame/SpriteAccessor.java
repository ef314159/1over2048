package com.me.mygdxgame;

import com.badlogic.gdx.graphics.g2d.Sprite;

import aurelienribon.tweenengine.TweenAccessor;

public class SpriteAccessor implements TweenAccessor<Sprite> {
	public static final int TWEEN_X = 0, TWEEN_Y = 1, TWEEN_SIZE = 2;

	@Override public int getValues(Sprite target, int tweenType, float[] returnValues) {
		switch(tweenType) {
			case TWEEN_X: returnValues[0] = target.getX(); break;
			case TWEEN_Y: returnValues[0] = target.getY(); break;
			case TWEEN_SIZE: returnValues[0] = target.getScaleX(); break;
		}
		return 1;
	}

	@Override public void setValues(Sprite target, int tweenType, float[] newValues) {
		switch(tweenType) {
			case TWEEN_X: target.setX(newValues[0]); break;
			case TWEEN_Y: target.setY(newValues[0]); break;
			case TWEEN_SIZE: target.setScale(newValues[0]); break;
		}
	}
}
