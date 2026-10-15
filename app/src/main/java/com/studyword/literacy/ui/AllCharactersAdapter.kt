package com.studyword.literacy.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.studyword.literacy.R
import com.studyword.literacy.databinding.ItemCharacterStatusBinding
import com.studyword.literacy.model.LearningCharacter

class AllCharactersAdapter(
    private val statusProvider: (LearningCharacter) -> CharacterStatus,
    private val onItemClicked: (LearningCharacter) -> Unit,
    /** v1.4.0:长按回调(可选);长按时若调,需 push 状态对话框 */
    private val onItemLongClicked: ((LearningCharacter) -> Unit)? = null
) : ListAdapter<LearningCharacter, AllCharactersAdapter.CharacterViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemCharacterStatusBinding.inflate(inflater, parent, false)
        return CharacterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CharacterViewHolder(private val binding: ItemCharacterStatusBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(character: LearningCharacter) {
            binding.characterText.text = character.hanzi
            binding.pinyinText.text = character.pinyin.ifBlank { "--" }

            val status = statusProvider(character)
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

            binding.root.setOnClickListener { onItemClicked(character) }
            onItemLongClicked?.let { longHandler ->
                    binding.root.setOnLongClickListener {
                        longHandler(character)
                        true
                    }
                }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<LearningCharacter>() {
            override fun areItemsTheSame(oldItem: LearningCharacter, newItem: LearningCharacter): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: LearningCharacter, newItem: LearningCharacter): Boolean =
                oldItem == newItem
        }
    }
}

enum class CharacterStatus {
    KNOWN,
    UNKNOWN,
    UNSEEN
}
