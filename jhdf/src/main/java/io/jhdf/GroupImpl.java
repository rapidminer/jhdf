package io.jhdf;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jhdf.exceptions.HdfException;
import io.jhdf.exceptions.UnsupportedHdfException;
import io.jhdf.object.message.AttributeMessage;
import io.jhdf.object.message.DataSpaceMessage;
import io.jhdf.object.message.LinkInfoMessage;
import io.jhdf.object.message.LinkMessage;
import io.jhdf.object.message.SymbolTableMessage;

public class GroupImpl implements Group {
	private final class AttributesLazyInitializer extends LazyInitializer<Map<String, AttributeMessage>> {
		private final LazyInitializer<ObjectHeader> lazyOjbectHeader;

		private AttributesLazyInitializer(LazyInitializer<ObjectHeader> lazyOjbectHeader) {
			this.lazyOjbectHeader = lazyOjbectHeader;
		}

		@Override
		protected Map<String, AttributeMessage> initialize() throws ConcurrentException {
			final ObjectHeader oh = lazyOjbectHeader.get();
			return oh.getMessagesOfType(AttributeMessage.class).stream()
					.collect(toMap(AttributeMessage::getName, identity()));
		}
	}

	private final class ChildrenLazyInitializer extends LazyInitializer<Map<String, Node>> {
		private final FileChannel fc;
		private final Superblock sb;
		private final Group parent;

		private ChildrenLazyInitializer(FileChannel fc, Superblock sb, Group parent) {
			this.fc = fc;
			this.sb = sb;
			this.parent = parent;
		}

		@Override
		protected Map<String, Node> initialize() throws ConcurrentException {
			logger.info("Loading children of '{}'", getPath());

			// Load the object header
			final ObjectHeader oh = objectHeader.get();

			if (oh.hasMessageOfType(SymbolTableMessage.class)) {
				// Its an old style Group
				final SymbolTableMessage stm = oh.getMessageOfType(SymbolTableMessage.class);
				final BTreeNode rootbTreeNode = new BTreeNode(fc, stm.getbTreeAddress(), sb);
				final LocalHeap rootNameHeap = new LocalHeap(fc, stm.getLocalHeapAddress(), sb);
				final ByteBuffer nameBuffer = rootNameHeap.getDataBuffer();

				final Map<String, Node> lazyChildren = new LinkedHashMap<>(rootbTreeNode.getEntriesUsed());

				for (long child : rootbTreeNode.getChildAddresses()) {
					GroupSymbolTableNode groupSTE = new GroupSymbolTableNode(fc, child, sb);
					for (SymbolTableEntry ste : groupSTE.getSymbolTableEntries()) {
						String childName = readName(nameBuffer, ste.getLinkNameOffset());
						final Node node;
						if (ste.getCacheType() == 1) { // Its a group
							node = createGroup(fc, sb, ste.getObjectHeaderAddress(), childName, parent);
						} else { // Dataset
							node = new Dataset(fc, sb, ste.getObjectHeaderAddress(), childName, parent);
						}
						lazyChildren.put(childName, node);
					}
				}
				return lazyChildren;

			} else {
				// Its a new style group
				final List<LinkMessage> links = oh.getMessagesOfType(LinkMessage.class);
				final LinkInfoMessage linkInfoMessage = oh.getMessageOfType(LinkInfoMessage.class);

				if (linkInfoMessage.getbTreeNameIndexAddress() == Constants.UNDEFINED_ADDRESS) {
					final Map<String, Node> lazyChildren = new LinkedHashMap<>(links.size());
					for (LinkMessage link : links) {
						ObjectHeader linkHeader = ObjectHeader.readObjectHeader(fc, sb, link.getHardLinkAddress());
						final Node node;
						if (linkHeader.hasMessageOfType(DataSpaceMessage.class)) {
							// Its a a Dataset
							node = new Dataset(fc, sb, link.getHardLinkAddress(), link.getLinkName(), parent);
						} else {
							// Its a group
							node = createGroup(fc, sb, link.getHardLinkAddress(), link.getLinkName(), parent);
						}
						lazyChildren.put(link.getLinkName(), node);

					}
					return lazyChildren;
				} else {
					throw new UnsupportedHdfException("Only compact link storage is supported");
				}
			}
		}

		private String readName(ByteBuffer bb, int linkNameOffset) {
			bb.position(linkNameOffset);
			return Utils.readUntilNull(bb);
		}
	}

	private static final Logger logger = LoggerFactory.getLogger(GroupImpl.class);

	private final String name;
	private final long address;
	private final Group parent;

	private final LazyInitializer<ObjectHeader> objectHeader;
	private final LazyInitializer<Map<String, Node>> children;
	private final LazyInitializer<Map<String, AttributeMessage>> attributes;

	private GroupImpl(FileChannel fc, Superblock sb, long address, String name, Group parent) {
		logger.trace("Creating group '{}'...", name);
		this.name = name;
		this.address = address;
		this.parent = parent;
		this.objectHeader = ObjectHeader.lazyReadObjectHeader(fc, sb, address);

		children = new ChildrenLazyInitializer(fc, sb, this);

		// Add attributes
		attributes = new AttributesLazyInitializer(objectHeader);

		logger.debug("Created group '{}'", getPath());
	}

	/**
	 * This is a special case constructor for the root group.
	 * 
	 * @param fc                  The file channel for reading the file
	 * @param sb                  The HDF superblock for this file
	 * @param objectHeaderAddress The offset into the file of the object header for
	 *                            this group
	 * @param parent              For the root group the parent is the file itself.
	 */
	private GroupImpl(FileChannel fc, Superblock sb, long objectHeaderAddress, HdfFile parent) {
		logger.trace("Creating root group...");
		this.name = ""; // Special case for root group no name
		this.address = objectHeaderAddress;
		this.parent = parent;
		this.objectHeader = ObjectHeader.lazyReadObjectHeader(fc, sb, objectHeaderAddress);

		// Special case for root group pass parent instead of this
		children = new ChildrenLazyInitializer(fc, sb, parent);

		// Add attributes
		attributes = new AttributesLazyInitializer(objectHeader);

		logger.debug("Created group '{}'", getPath());
	}

	/**
	 * Creates a group for the specified object header with the given name by
	 * reading from the file channel.
	 * 
	 * @param fc                  The file channel for reading the file
	 * @param sb                  The HDF superblock for this file
	 * @param objectHeaderAddress The offset into the file of the object header for
	 *                            this group
	 * @param name                The name of this group
	 * @param parent              For the root group the parent is the file itself.
	 * @return The newly read group
	 */
	/* package */ static Group createGroup(FileChannel fc, Superblock sb, long objectHeaderAddress, String name,
			Group parent) {
		return new GroupImpl(fc, sb, objectHeaderAddress, name, parent);
	}

	/* package */ static Group createRootGroup(FileChannel fc, Superblock sb, long objectHeaderAddress, HdfFile file) {
		// Call the special root group constructor
		return new GroupImpl(fc, sb, objectHeaderAddress, file);
	}

	@Override
	public boolean isGroup() {
		return true;
	}

	@Override
	public Map<String, Node> getChildren() {
		try {
			return children.get();
		} catch (ConcurrentException e) {
			throw new HdfException(
					"Failed to load chirdren for group '" + getPath() + "' at address '" + getAddress() + "'", e);
		}
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return "Group [name=" + name + ", path=" + getPath() + ", address=" + Utils.toHex(getAddress()) + "]";
	}

	@Override
	public String getPath() {
		return parent.getPath() + name + "/";
	}

	@Override
	public Map<String, AttributeMessage> getAttributes() {
		try {
			return attributes.get();
		} catch (ConcurrentException e) {
			throw new HdfException(
					"Failed to load attributes for group '" + getPath() + "' at address '" + getAddress() + "'", e);
		}
	}

	@Override
	public String getType() {
		return "Group";
	}

	@Override
	public Node getParent() {
		return parent;
	}

	@Override
	public long getAddress() {
		return address;
	}

}