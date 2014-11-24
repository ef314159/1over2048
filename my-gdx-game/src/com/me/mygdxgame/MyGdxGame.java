package com.me.mygdxgame;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenEquations;
import aurelienribon.tweenengine.TweenManager;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.Timer.Task;

/**
 * doing everything in one class yaaaaaaaaaay lol
 * @author ef314159
 */
public class MyGdxGame implements ApplicationListener, InputProcessor {
	// LibGDX rendering stuffs and background sprite.
	private OrthographicCamera camera;
	private SpriteBatch batch;
	private Sprite bgSprite, mouseIndicator, gameOverLay; // haha overlay
	
	private Sprite restart;
	private Texture restart0, restart1;
	
	private int mouseX = 0, mouseY = 0;
	
	private Tile[][] grid = new Tile[4][4];
	private List<Tile> transit = new LinkedList<Tile>();
	private List<Tile> newTiles = new LinkedList<Tile>();
	private TweenManager manager = new TweenManager();
	private Timer moveTimer;
	private Random RNG = new Random();
	
	private boolean gameOver = false;
	
	// times for tween animations and respective AI delays
	// time for a tile to move from one position to another
	private final float MOVE_TIME = 0.1f;
	// time for a tile to appear (scale: 0 -> 1)
	private final float APPEAR_TIME = 0.1f;
	// time for a merged tile to "pop" (scale: 1 -> 1)
	private final float MERGE_TIME = 0.05f;
	
	// direction constants. fuck enums 
	private static final int D_LEFT = 0, D_RIGHT = 1, D_DOWN = 2, D_UP = 3;
	
	// tile textures: tiles[0] is 1/2, tiles[1] is 1/4, etc
	private Texture[] tiles;
	
	@Override public void create() {		
		float w = Gdx.graphics.getWidth();
		float h = Gdx.graphics.getHeight();
		
		Tween.registerAccessor(Tile.class, new TileAccessor());
		Tween.registerAccessor(Sprite.class, new SpriteAccessor());
		
		camera = new OrthographicCamera(w, h);
		batch = new SpriteBatch();
		
		tiles = new Texture[11];
		tiles[0] = new Texture(Gdx.files.internal("2.png"));
		tiles[1] = new Texture(Gdx.files.internal("4.png"));
		tiles[2] = new Texture(Gdx.files.internal("8.png"));
		tiles[3] = new Texture(Gdx.files.internal("16.png"));
		tiles[4] = new Texture(Gdx.files.internal("32.png"));
		tiles[5] = new Texture(Gdx.files.internal("64.png"));
		tiles[6] = new Texture(Gdx.files.internal("128.png"));
		tiles[7] = new Texture(Gdx.files.internal("256.png"));
		tiles[8] = new Texture(Gdx.files.internal("512.png"));
		tiles[9] = new Texture(Gdx.files.internal("1024.png"));
		tiles[10] = new Texture(Gdx.files.internal("2048.png"));
		
		restart0 = new Texture(Gdx.files.internal("restart0.png"));
		restart1 = new Texture(Gdx.files.internal("restart1.png"));
		
		Gdx.input.setInputProcessor(this);
		
		bgSprite = new Sprite(new Texture(Gdx.files.internal("background.png")));
		bgSprite.setPosition(-bgSprite.getWidth()/2, -bgSprite.getHeight()/2);
		mouseIndicator = new Sprite(new Texture(Gdx.files.internal("mouseindicator.png")));
		
		moveTimer = new Timer();
	}

	public static float getScreenCoords(int tile) {
		return (float)tile*128 - 256;
	}
	
	public static int getTileCoords(float screenCoord) {
		return (int)((screenCoord + 256)/128);
	}
	
	@Override public void dispose() {
		batch.dispose();
	}

	@Override public void render() {		
		Gdx.gl.glClearColor(1, 1, 1, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		
		batch.setProjectionMatrix(camera.combined);
		batch.begin();
		bgSprite.draw(batch);
		
		int gridX = getTileCoords(mouseX - Gdx.graphics.getWidth()/2);
		int gridY = getTileCoords(-(mouseY - Gdx.graphics.getHeight()/2));
		
		if (gridX >= 0 && gridX <= 3 && gridY >= 0 && gridY <= 3 &&
				grid[gridX][gridY] == null && !gameOver) {
			mouseIndicator.setX(getScreenCoords(gridX));
			mouseIndicator.setY(getScreenCoords(gridY));
			mouseIndicator.draw(batch);
		}
		
		for (Tile[] array : grid) {
			for (Tile t : array) {
				if (t != null) t.draw(batch);
			}
		}
		
		for (Tile t : this.transit) {
			t.draw(batch);
		}
		
		if (gameOver) {
			gameOverLay.draw(batch);
			
			int x = mouseX - Gdx.graphics.getWidth()/2, y = -mouseY + Gdx.graphics.getHeight()/2;
			if (x > restart.getX() && x < restart.getX() + restart.getWidth() && 
					y > restart.getY() && y < restart.getY() + restart.getHeight()) {
				restart.setTexture(restart1);
			} else {
				restart.setTexture(restart0);
			}
			restart.draw(batch);
		}
		
		batch.end();
		
		manager.update(Gdx.graphics.getDeltaTime());
	}

	// arrays for AI movement during touchUp()
	// defines whether a move in a given direction is possible
	boolean moves[] = new boolean[4];
	// defines the value of a given move based on the tiles it merges, currently broken
	//int value[] = new int[4];
	
	@Override public boolean mouseMoved(int screenX, int screenY) {
		mouseX = screenX;
		mouseY = screenY;
		return true;
	}
	
	/**
	 * Called on any mouse click. Creates a "1/2" block on the selected square and causes
	 * AI to make a move. Moves are selected randomly.
	 */
	@Override public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		int x = screenX - Gdx.graphics.getWidth()/2, y = -screenY + Gdx.graphics.getHeight()/2;
		if (gameOver) {
			if (x > restart.getX() && x < restart.getX() + restart.getWidth() && 
					y > restart.getY() && y < restart.getY() + restart.getHeight()) {

				restart();
				return true;
			}
			return false;
		} else {
			// convert input from screen coords, to sprite coords, to grid coords
			int gridX = getTileCoords(x);
			int gridY = getTileCoords(y);
			
			// if not a valid tile, return.
			if (gridX < 0 || gridX > 3 || gridY < 0 || gridY > 3 || grid[gridX][gridY] != null) {
				return false;
			}
			/* LibGDX specifies that the return value is "whether the input was processed".
			 * Return false here since the input did nothing. Not sure what the correct
			 * convention is.
			 */
			
			// create new tile and scale it up using tweenengine (initial scale is 0 so it can appear)
			Tile t = new Tile(tiles[0], 0, gridX, gridY);
			Tween.to(t, TileAccessor.TWEEN_SIZE, APPEAR_TIME).target(1).start(manager);
			grid[gridX][gridY] = t;
			
			// schedule the AI action (or game over if no AI move is possible).
			moveTimer.scheduleTask(new Task(){
				@Override public void run() {
					boolean movesExist = false;
					// non-functional AI strategy based on value. 
					//int mostValue = -1;
					
					// find out which directions are usable
					for (int i = 0; i < 4; ++i) {
						moves[i] = canMove(i);
						if (moves[i]) movesExist = true;
					}
					
					// more code for value strategy
					/*for (int i = 0; i < 4; ++i) {
						value[i] = moveValue(i);
						if (mostValue == -1 || value[i] > value[mostValue]) mostValue = i;
					}*/
					
					if (movesExist) {
						int direction = -1;
						do {
							// select direction randomly
							direction = RNG.nextInt(4);
							
							// select direction based on priority - 3 only used in worst-case scenario
							//direction++;
						} while (!moves[direction]);
						
						move(direction);
						//move(mostValue);
						
						for (int i = 0; i < 4; ++i) {
							for (int j = 0; j < 4; ++j) {
								if (grid[i][j] != null && grid[i][j].getNumber() == 10) endGame(true);
							}
						}
					} else {
						endGame(false);
					}
				}
			}, APPEAR_TIME);
			
			return true;
		}
	}
	
	private void endGame(boolean won) {
		gameOver = true;
		
		gameOverLay = new Sprite(new Texture(Gdx.files.internal("gameover" + (won ? 1 : 0) + ".png")));
		gameOverLay.setPosition(-gameOverLay.getWidth()/2, -gameOverLay.getHeight()/2);
		gameOverLay.setScale(0);
		Tween.to(gameOverLay, SpriteAccessor.TWEEN_SIZE, APPEAR_TIME).target(1).start(manager);
		
		restart = new Sprite(restart0);
		restart.setPosition(-restart.getWidth()/2, -restart.getHeight()/2 - 64);
	}
	
	private void restart() {
		for (int i = 0; i < 4; ++i) {
			for (int j = 0; j < 4; ++j) {
				grid[i][j] = null;
			}
		}
		transit.clear();
		newTiles.clear();
		gameOver = false;
	}
	
	/**
	 * Iterates through the grid and returns whether a move is possible in a given direction.
	 * A move is "possible" if it would result in any sliding or merging of tiles.
	 * @param direction The direction to check for an available move as a number in 0..3
	 * @return whether a move in this direction would result in any change
	 */
	private boolean canMove(int direction) {
		switch (direction) {
			case D_LEFT:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 0; j < 4; ++j) {
						if (grid[j][i] == null) spaces++;
						else {
							if (j-spaces > 0 &&
									grid[j-spaces-1][i] != null &&
									grid[j-spaces-1][i].getNumber() == grid[j][i].getNumber()) {
								spaces++;
							}
		
							if (spaces > 0) {
								return true;
							}
						}
					}
				}
				break;
				
			case D_RIGHT:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 3; j >= 0; --j) {
						if (grid[j][i] == null) spaces++;
						else {
							if (j+spaces < 3 &&
									grid[j+spaces+1][i] != null &&
									grid[j+spaces+1][i].getNumber() == grid[j][i].getNumber()) {
								spaces++;
							}
		
							if (spaces > 0) {
								return true;
							}
						}
					}
				}
				break;
				
			case D_UP:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 3; j >= 0; --j) {
						if (grid[i][j] == null) spaces++;
						else {
							if (j+spaces < 3 &&
									grid[i][j+spaces+1] != null &&
									grid[i][j+spaces+1].getNumber() == grid[i][j].getNumber()) {
								spaces++;
							}
		
							if (spaces > 0) {
								return true;
							}
						}
					}
				}
				break;
				
			case D_DOWN:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 0; j < 4; ++j) {
						if (grid[i][j] == null) spaces++;
						else {
							if (j-spaces > 0 &&
									grid[i][j-spaces-1] != null &&
									grid[i][j-spaces-1].getNumber() == grid[i][j].getNumber()) {
								spaces++;
							}
		
							if (spaces > 0) {
								return true;
							}
						}
					}
				}
				break;
			}
		return false;
	}
	
	/**
	 * Returns the value of a given move. Value is calculated based on the value of merged
	 * tiles after the move. A move that merges no tiles is worth 0, a move that merges two
	 * 1/2 tiles is worth 2, A move that merges two 1/16 tiles is worth 16, etc.
	 * 
	 * Currently broken because I'm dumb. TODO: fix
	 * 
	 * @param direction The direction to check for move value as a number in 0..3
	 * @return the value of a move in that direction
	 */
	private int moveValue(int direction) {
		int points = 0;
		switch (direction) {
			case D_LEFT:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 0; j < 4; ++j) {
						if (grid[j][i] == null) spaces++;
						else {
							if (j-spaces > 0 &&
									grid[j-spaces-1][i] != null &&
									grid[j-spaces-1][i].getNumber() == grid[j][i].getNumber()) {
								//spaces++;
								points += Math.pow(2, grid[j][i].getNumber());
							}
						}
					}
				}
				break;
				
			case D_RIGHT:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 3; j >= 0; --j) {
						if (grid[j][i] == null) spaces++;
						else {
							if (j+spaces < 3 &&
									grid[j+spaces+1][i] != null &&
									grid[j+spaces+1][i].getNumber() == grid[j][i].getNumber()) {
								//spaces++;
								points += Math.pow(2, grid[j][i].getNumber());
							}
						}
					}
				}
				break;
				
			case D_UP:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 3; j >= 0; --j) {
						if (grid[i][j] == null) spaces++;
						else {
							if (j+spaces < 3 &&
									grid[i][j+spaces+1] != null &&
									grid[i][j+spaces+1].getNumber() == grid[i][j].getNumber()) {
								//spaces++;
								points += Math.pow(2, grid[i][j].getNumber());
							}
						}
					}
				}
				break;
				
			case D_DOWN:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 0; j < 4; ++j) {
						if (grid[i][j] == null) spaces++;
						else {
							if (j-spaces > 0 &&
									grid[i][j-spaces-1] != null &&
									grid[i][j-spaces-1].getNumber() == grid[i][j].getNumber()) {
								//spaces++;
								points += Math.pow(2, grid[i][j].getNumber());
							}
						}
					}
				}
				break;
			}
		return points;
	}
	
	/**
	 * Moves all tiles on the grid in a given direction, and merges tiles of equal numbers
	 * if they collide. Adds tiles to the "transit" list if they merge with other tiles, so
	 * they can be displayed even though they are no longer on the grid. Schedules a timer 
	 * task to run after tweening is finished to display the new (merged) tiles and clear
	 * the transit and newTiles lists.
	 * @param direction The direction to move as a number in 0..3
	 */
	private void move (int direction) {
		switch (direction) {
			case D_LEFT:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 0; j < 4; ++j) {
						if (grid[j][i] == null) spaces++;
						else {
							Tile t = grid[j][i];
							
							if (j-spaces > 0 &&
									grid[j-spaces-1][i] != null &&
									grid[j-spaces-1][i].getNumber() == t.getNumber()) {
								Tile newTile = new Tile(
										tiles[t.getNumber()+1],
										t.getNumber()+1,
										j-spaces-1, i);
								transit.add(grid[j-spaces-1][i]);
								grid[j-spaces-1][i] = newTile;
								newTiles.add(newTile);
								spaces++;
								transit.add(t);
							} else {
								grid[j-spaces][i] = t;
							}

							if (spaces > 0) {
								Tween.to(t, TileAccessor.TWEEN_X, MOVE_TIME)
									.target(t.getX() - spaces*128)
									.start(manager);
								grid[j][i] = null;
							}
						}
					}
				}
				break;
				
			case D_RIGHT:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 3; j >= 0; --j) {
						if (grid[j][i] == null) spaces++;
						else {
							Tile t = grid[j][i];
							
							if (j+spaces < 3 &&
									grid[j+spaces+1][i] != null &&
									grid[j+spaces+1][i].getNumber() == t.getNumber()) {
								Tile newTile = new Tile(
										tiles[t.getNumber()+1],
										t.getNumber()+1,
										j+spaces+1, i);
								transit.add(grid[j+spaces+1][i]);
								grid[j+spaces+1][i] = newTile;
								newTiles.add(newTile);
								spaces++;
								transit.add(t);
							} else {
								grid[j+spaces][i] = t;
							}

							if (spaces > 0) {
								Tween.to(t, TileAccessor.TWEEN_X, MOVE_TIME)
									.target(t.getX() + spaces*128)
									.start(manager);
								grid[j][i] = null;
							}
						}
					}
				}
				break;
				
			case D_UP:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 3; j >= 0; --j) {
						if (grid[i][j] == null) spaces++;
						else {
							Tile t = grid[i][j];
							
							if (j+spaces < 3 &&
									grid[i][j+spaces+1] != null &&
									grid[i][j+spaces+1].getNumber() == t.getNumber()) {
								Tile newTile = new Tile(
										tiles[t.getNumber()+1],
										t.getNumber()+1,
										i, j+spaces+1);
								transit.add(grid[i][j+spaces+1]);
								grid[i][j+spaces+1] = newTile;
								newTiles.add(newTile);
								spaces++;
								transit.add(t);
							} else {
								grid[i][j+spaces] = t;
							}

							if (spaces > 0) {
								Tween.to(t, TileAccessor.TWEEN_Y, MOVE_TIME)
									.target(t.getY() + spaces*128)
									.start(manager);
								grid[i][j] = null;
							}
						}
					}
				}
				break;
				
			case D_DOWN:
				for (int i = 0; i < 4; ++i) {
					int spaces = 0;
					for (int j = 0; j < 4; ++j) {
						if (grid[i][j] == null) spaces++;
						else {
							Tile t = grid[i][j];
							
							if (j-spaces > 0 &&
									grid[i][j-spaces-1] != null &&
									grid[i][j-spaces-1].getNumber() == t.getNumber()) {
								Tile newTile = new Tile(
										tiles[t.getNumber()+1],
										t.getNumber()+1,
										i, j-spaces-1);
								transit.add(grid[i][j-spaces-1]);
								grid[i][j-spaces-1] = newTile;
								newTiles.add(newTile);
								spaces++;
								transit.add(t);
							} else {
								grid[i][j-spaces] = t;
							}

							if (spaces > 0) {
								Tween.to(t, TileAccessor.TWEEN_Y, MOVE_TIME)
									.target(t.getY() - spaces*128)
									.start(manager);
								grid[i][j] = null;
							}
						}
					}
				}
				break;
		}
		
		moveTimer.scheduleTask(new Task(){
			@Override public void run() {
				clearTransit();
			}
		}, MOVE_TIME);
	}
	
	/**
	 * Called after an AI move. Clears the list of merged tiles in transit so they are no
	 * longer displayed and can be GCed, and displays the "pop" animation for new tiles
	 * using the tween engine.
	 */
	private void clearTransit() {
		transit.clear();
		
		for (Tile t : newTiles) {
			t.setSize(1);
			Tween.to(t, TileAccessor.TWEEN_SIZE, MERGE_TIME).target(1.1f).repeatYoyo(1, 0)
					.ease(TweenEquations.easeOutSine).start(manager);
		}
		
		newTiles.clear();
	}

	// herp de derp
	@Override public void resize(int width, int height) { }
	@Override public void pause() { }
	@Override public void resume() { }
	@Override public boolean keyDown(int keycode) { return false; }
	@Override public boolean keyUp(int keycode) { return false; }
	@Override public boolean keyTyped(char character) { return false; }
	@Override public boolean touchDown(int screenX, int screenY, int pointer, int button) { return false; }
	@Override public boolean touchDragged(int screenX, int screenY, int pointer) { return false; }
	@Override public boolean scrolled(int amount) { return false; }
}
