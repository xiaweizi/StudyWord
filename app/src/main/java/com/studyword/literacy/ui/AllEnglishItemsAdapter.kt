package com.studyword.literacy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyword.literacy.R
import com.studyword.literacy.databinding.ItemEnglishStatusBinding
import com.studyword.literacy.model.StudyItem

/**
 * 字库英文项 Adapter。
 *
 * 把 [StudyItem](letter / word)统一渲染成 "Aa + 音标 + 状态标签" 形式。
 * Letter:大字 "Aa",音标 "/eɪ/"
 * Word:大字 "cat",音标 "/kæt/"
 *
 * 状态:[CharacterStatus] 三态(KNOWN / UNKNOWN / UNSEEN)
 */
class AllEnglishItemsAdapter(
    private val statusProvider: (StudyItem) -> CharacterStatus,
    private val onItemClicked: (StudyItem) -> Unit,
    /** v1.4.0:长按回调(可选);长按时若调,需 push 状态对话框 */
    private val onItemLongClicked: ((StudyItem) -> Unit)? = null
) : ListAdapter<StudyItem, AllEnglishItemsAdapter.EnglishViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EnglishViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemEnglishStatusBinding.inflate(inflater, parent, false)
        return EnglishViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EnglishViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class EnglishViewHolder(private val binding: ItemEnglishStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StudyItem) {
            // letter → "Aa";word → 单词
            binding.characterText.text = item.primaryText + item.tertiaryText
            binding.phoneticText.text = item.secondaryText.ifBlank { "--" }

            val status = statusProvider(item)
            val (labelRes, backgroundRes, textColorRes) = when (status) {
                CharacterStatus.KNOWN -> Triple(R.string.status_known, R.drawable.bg_status_known, R.color.white)
                CharacterStatus.UNKNOWN -> Triple(R.string.status_unknown, R.drawable.bg_status_unknown, R.color.white)
                CharacterStatus.UNSEEN -> Triple(R.string.status_unseen, R.drawable.bg_status_unseen, R.color.deep_blue)
            }
            binding.statusLabel.setText(labelRes)
            binding.statusLabel.setBackgroundResource(backgroundRes)
            binding.statusLabel.setTextColor(
                androidx.core.content.ContextCompat.getColor(binding.statusLabel.context, textColorRes)
            )

            binding.root.setOnClickListener { onItemClicked(item) }
            onItemLongClicked?.let { longHandler ->
                    binding.root.setOnLongClickListener {
                        longHandler(item)
                        true
                    }
                }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<StudyItem>() {
            override fun areItemsTheSame(oldItem: StudyItem, newItem: StudyItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: StudyItem, newItem: StudyItem): Boolean =
                oldItem == newItem
        }
    }
}
