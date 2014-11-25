package com.me.mygdxgame;

import java.util.LinkedList;
import java.util.List;

import aurelienribon.tweenengine.Tween;
import aurelienribon.tweenengine.TweenEquations;
import aurelienribon.tweenengine.TweenManager;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Timer;
import com.badlogic.gdx.utils.Timer.Task;

/**
 * Represents a 2048 board. Acts as model, view, and controller, updating game
 * state, rendering the board and accepting input.
 * @author ef314159
 */
public class MyGdxGame implements ApplicationListener, InputProcessor {
	// LibGDX rendering stuffs and background sprite.
	private OrthographicCamera camera;
	private SpriteBatch batch;
	private Sprite bgSprite, mouseIndicator, gameOverLay;
	
	// Sprite and texture for the restart button
	private Sprite restart;
	private Texture restart0, restart1;
	
	// stored mouse location to display tinted tile
	private int mouseX = 0, mouseY = 0;
	
	// the board itself as a Tile array
	private Tile[][] grid = new Tile[4][4];
	
	// tiles that are currently tweening (tiles are never alone in these lists,
	//  they always have a location on the grid as well)
	private List<Tile> transit = new LinkedList<Tile>();
	private List<Tile> newTiles = new LinkedList<Tile>();
	
	// time awareness and tweening stuffs
	private TweenManager manager = new TweenManager();
	private Timer moveTimer;
	
	// times for tween animations and respective AI delays
	// time for a tile to move from one position to another
	private final float MOVE_TIME = 0.1f;
	// time for a tile to appear (scale: 0 -> 1)
	private final float APPEAR_TIME = 0.1f;
	// time for a merged tile to "pop" (scale: 1 -> 1)
	private final float MERGE_TIME = 0.05f;
	
	private boolean gameOver = false;
	
	// direction constants.
	private static final int D_LEFT = 0, D_RIGHT = 1, D_DOWN = 2, D_UP = 3;
	
	// tile textures: tiles[0] is the 1/2 tile, tiles[1] is 1/4, etc
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
					int mostValue = -1;
					int value[] = new int[4];
					
					for (int i = 0; i < 4; ++i) {
						if (!canMove(i)) value[i] = -1;
						else {
							value[i] = moveValue(i, grid, 3);
							if (mostValue == -1 || value[i] > value[mostValue]) mostValue = i;
						}
						System.out.print(value[i] + " ");
					}
					System.out.println("");
					
					if (mostValue > -1) {
						move(mostValue, grid, true);
						
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
	
	/**
	 * Ends the game. Displays an overlay and a button sprite for restarting.
	 * @param won whether the game ended in success
	 */
	private void endGame(boolean won) {
		gameOver = true;
		
		gameOverLay = new Sprite(new Texture(Gdx.files.internal("gameover" + (won ? 1 : 0) + ".png")));
		gameOverLay.setPosition(-gameOverLay.getWidth()/2, -gameOverLay.getHeight()/2);
		gameOverLay.setScale(0);
		Tween.to(gameOverLay, SpriteAccessor.TWEEN_SIZE, APPEAR_TIME).target(1).start(manager);
		
		restart = new Sprite(restart0);
		restart.setPosition(-restart.getWidth()/2, -restart.getHeight()/2 - 64);
	}
	
	/**
	 * Clears the entire board and the lists of any tiles still tweening.
	 */
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
	 * @param direction The direction to check for move value as a number in 0..3
	 * @return the value of a move in that direction
	 */
	private int moveValue(int direction, Tile[][] grid, int lookAhead) {
		Tile[][] clone = cloneGrid(grid);
		move(direction, clone, false);
		return movePotential(clone, lookAhead);
	}
	
	/**
	 * Calculates the value of a grid, taking into account moves that can be
	 * made from that position. This algorithm simplifies the game to assume
	 * that no new tiles will be placed.
	 * 
	 * @param grid the grid to analyze
	 * @param lookAhead the number of potential moves to look ahead
	 * @return the grid's value
	 */
	private int movePotential(Tile[][] grid, int lookAhead) {
		int points = -1;
		
		if (lookAhead == 0) {
			return gridValue(grid);
		} else {
			for (int i = 0; i < 4; ++i) {
				Tile[][] clone = cloneGrid(grid);
				move(i, clone, false);
				points = Math.max(points, movePotential(clone, lookAhead - 1));
			}
		}
		
		// future potential is divided by 2 and added to the current value
		// this prioritizes marging valuable tiles as fast as possible
		return points/2 + gridValue(grid);
	}
	
	/**
	 * Calculates the value for an entire grid. The naive way to do this is to
	 * sum up the values of all the tiles in the grid, so that a grid with a 4
	 * tile has a better value than a grid with two 2 tiles.
	 * @param grid the grid to sum up the value of
	 * @return the summed value of the grid
	 */
	private int gridValue(Tile[][] grid) {
		int points = 0;

		for (int i = 0; i < 4; ++i) {
			for (int j = 0; j < 4; ++j) {
				if (grid[i][j] != null) points += tileValue(grid[i][j]);
			}
		}
		
		return points;
	}
	
	/**
	 * Calculates the AI's value for a single tile, using the tile.getNumber()
	 * accessor. 2^getNumber() would give the visible 2,4,8... values, but
	 * would result in two 2 tiles weighing the same as a single 4 tile. This
	 * method uses 4^getNumber() to prioritize marging tiles.
	 * @param t the tile to get the value of
	 * @return the tile's value
	 */
	private int tileValue(Tile t) {
		return (int)Math.pow(4, t.getNumber());
	}
	
	/**
	 * Clones a grid so that move() may be called on it without affecting the
	 * original grid.
	 * @param grid the grid to clone
	 * @return a new grid with the same tiles as the original
	 */
	private Tile[][] cloneGrid(Tile[][] grid) {
		Tile[][] clone = new Tile[4][4];

		for (int i = 0; i < 4; ++i) {
			for (int j = 0; j < 4; ++j) {
				clone[i][j] = grid[i][j];
			}
		}
		
		return clone;
	}
	
	/**
	 * Moves all tiles on the grid in a given direction, and merges tiles of
	 * equal numbers if they collide.
	 * 
	 * May also add tiles to the "transit" list if they merge with other tiles,
	 * so they can be displayed even though they are no longer on the grid, and
	 * schedule a timer  task to run after tweening is finished to display the
	 * new (merged) tiles and clear the transit and newTiles lists.
	 * 
	 * @param direction The direction to move as a number in 0..3
	 * @param grid the grid on which to do the move
	 * @param whether to add any moving or merged tiles to the tween lists
	 */
	private void move (int direction, Tile[][] grid, boolean doTweens) {
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
								if (doTweens) {
									Tween.to(t, TileAccessor.TWEEN_X, MOVE_TIME)
										.target(t.getX() - spaces*128)
										.start(manager);
								}
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
								if (doTweens) {
									Tween.to(t, TileAccessor.TWEEN_X, MOVE_TIME)
										.target(t.getX() + spaces*128)
										.start(manager);
								}
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
								if (doTweens) {
									Tween.to(t, TileAccessor.TWEEN_Y, MOVE_TIME)
										.target(t.getY() + spaces*128)
										.start(manager);
								}
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
								if (doTweens) {
									Tween.to(t, TileAccessor.TWEEN_Y, MOVE_TIME)
										.target(t.getY() - spaces*128)
										.start(manager);
								}
								grid[i][j] = null;
							}
						}
					}
				}
				break;
		}
		
		if (doTweens) {
			moveTimer.scheduleTask(new Task(){
				@Override public void run() {
					clearTransit();
				}
			}, MOVE_TIME);
		}
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
