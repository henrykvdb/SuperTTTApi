package com.flaghacker.uttt.bots;

import com.flaghacker.uttt.common.Board;
import com.flaghacker.uttt.common.Bot;
import com.flaghacker.uttt.common.Coord;

import java.util.List;
import java.util.Random;

public class RandomBot implements Bot
{
	private Random random = new Random(0);

	@Override
	public Coord move(Board board)
	{
		List<Coord> moves = board.availableMoves();
		return moves.get(random.nextInt(moves.size()));
	}
}