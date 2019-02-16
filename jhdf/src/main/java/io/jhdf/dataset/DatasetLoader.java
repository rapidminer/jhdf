package io.jhdf.dataset;

import java.nio.channels.FileChannel;

import io.jhdf.ObjectHeader;
import io.jhdf.Superblock;
import io.jhdf.api.Dataset;
import io.jhdf.api.Group;
import io.jhdf.exceptions.HdfException;
import io.jhdf.exceptions.UnsupportedHdfException;
import io.jhdf.object.message.DataLayoutMessage;
import io.jhdf.object.message.DataLayoutMessage.ChunkedDataLayoutMessageV3;
import io.jhdf.object.message.DataLayoutMessage.ChunkedDataLayoutMessageV4;
import io.jhdf.object.message.DataLayoutMessage.CompactDataLayoutMessage;
import io.jhdf.object.message.DataLayoutMessage.ContigiousDataLayoutMessage;

public final class DatasetLoader {

	private DatasetLoader() {
		// No instances
	}

	public static Dataset createDataset(FileChannel fc, Superblock sb, long address, String name,
			Group parent) {

		try {
			// Load the object header to determine the type of dataset to make
			final ObjectHeader oh = ObjectHeader.readObjectHeader(fc, sb, address);
			DataLayoutMessage dlm = oh.getMessageOfType(DataLayoutMessage.class);

			if (dlm instanceof CompactDataLayoutMessage) {
				return new CompactDataset(fc, sb, address, name, parent, oh);

			} else if (dlm instanceof ContigiousDataLayoutMessage) {
				return new ContigiousDataset(fc, sb, address, name, parent, oh);

			} else if (dlm instanceof ChunkedDataLayoutMessageV3) {
				return new ChunkedDatasetV3(fc, sb, address, name, parent, oh);

			} else if (dlm instanceof ChunkedDataLayoutMessageV4) {
				throw new UnsupportedHdfException("Chunked V4 dataset not supported");

			} else {
				throw new HdfException("Unreconised Dataset layout type: " + dlm.getClass().getCanonicalName());
			}

		} catch (Exception e) {
			throw new HdfException("Failed to read dataset '" + name + "' at address '" + address + "'", e);
		}
	}

}
