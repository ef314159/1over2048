package com.me.mygdxgame;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Tile {
	private Sprite sprite;
	private int number;

	public Tile(Texture tex, int number, int x, int y) {
		this.sprite = new Sprite(tex);
		this.number = number;
		setX(MyGdxGame.getScreenCoords(x));
		setY(MyGdxGame.getScreenCoords(y));
		
		sprite.setScale(0);
	}
	
	public void draw(SpriteBatch batch) {
		sprite.draw(batch);
	}
	
	public float getX() {
		return sprite.getX();
	}
	
	public float getY() {
		return sprite.getY();
	}
	
	public float getSize() {
		return sprite.getScaleX();
	}
	
	public int getNumber() {
		return number;
	}
	
	public void setX(float x) {
		sprite.setX(x);
	}
	
	public void setY(float y) {
		sprite.setY(y);
	}
	
	public void setSize(float size) {
		sprite.setScale(size);
	}
}
