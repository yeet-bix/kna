package org.jire.kna.cached.page

import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.openhft.chronicle.core.OS
import org.jire.kna.Pointer
import org.jire.kna.cached.CachedReadableSource

interface PageCachedReadableSource : CachedReadableSource {
	
	private companion object {
		val cachedPages: ThreadLocal<Long2ObjectMap<JNAPage>> =
			ThreadLocal.withInitial { Long2ObjectOpenHashMap() }
		val pageSize = OS.pageSize().toLong()
		val pageSizeTrailingZeroBits = pageSize.countTrailingZeroBits()
		
		private fun Long.toPageIndex(trailingZeroBits: Int = pageSizeTrailingZeroBits) =
			(this ushr trailingZeroBits) shl trailingZeroBits
	}
	
	override fun readCached(address: Long, bytesToRead: Long): Pointer {
		if (bytesToRead > pageSize || address < pageSize) return readSource(address, bytesToRead)
		
		val pageIndex = address.toPageIndex()
		val pageEndIndex = (address + bytesToRead).toPageIndex()
		if (pageIndex != pageEndIndex || address < pageIndex) return readSource(address, bytesToRead)
		val offset = address - pageIndex
		
		val pages = cachedPages.get()
		
		var page = pages[pageIndex]
		if (page == null) {
			page = JNAPage(this, pageSize, cacheExpireMillis)
			pages[pageIndex] = page
		}
		
		if (page.needsRead()) {
			if (!page.read(pageIndex)) return readSource(address, bytesToRead)
		}
		
		return page.address.offsetPointer(offset)
	}
	
}