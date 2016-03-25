package database.serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import org.apache.log4j.Logger;
import org.mapdb.Serializer;

import qora.block.Block;
import qora.block.BlockFactory;

public class BlockSerializer implements Serializer<Block>, Serializable
{
	
	private static final Logger LOGGER = Logger
			.getLogger(BlockSerializer.class);
	private static final long serialVersionUID = -6538913048331349777L;

	@Override
	public void serialize(DataOutput out, Block value) throws IOException 
	{
		out.writeInt(value.getDataLength());
        out.write(value.toBytes());
    }

    @Override
    public Block deserialize(DataInput in, int available) throws IOException 
    {
    	int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        try 
        {
        	return BlockFactory.getInstance().parse(bytes);
		} 
        catch (Exception e) 
        {
        	LOGGER.error(e.getMessage(),e);
		}
		return null;
    }

    @Override
    public int fixedSize() 
    {
    	return -1;
    }
}
