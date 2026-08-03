package com.nbp.cobblemon_smartphone.network

import com.cobblemon.mod.common.net.PacketRegisterInfo
import com.nbp.cobblemon_smartphone.network.handler.CallActionHandler
import com.nbp.cobblemon_smartphone.network.handler.CallOfflineHandler
import com.nbp.cobblemon_smartphone.network.handler.CallStateHandler
import com.nbp.cobblemon_smartphone.network.handler.CreatePostHandler
import com.nbp.cobblemon_smartphone.network.handler.DeletePostHandler
import com.nbp.cobblemon_smartphone.network.handler.ExecuteDatapackActionHandler
import com.nbp.cobblemon_smartphone.network.handler.FeedPageHandler
import com.nbp.cobblemon_smartphone.network.handler.HealPokemonHandler
import com.nbp.cobblemon_smartphone.network.handler.LikePostHandler
import com.nbp.cobblemon_smartphone.network.handler.MarkThreadReadHandler
import com.nbp.cobblemon_smartphone.network.handler.NewDmHandler
import com.nbp.cobblemon_smartphone.network.handler.RequestFeedPageHandler
import com.nbp.cobblemon_smartphone.network.handler.RequestThreadListHandler
import com.nbp.cobblemon_smartphone.network.handler.RequestThreadPageHandler
import com.nbp.cobblemon_smartphone.network.handler.SendDmHandler
import com.nbp.cobblemon_smartphone.network.handler.SyncUnreadHandler
import com.nbp.cobblemon_smartphone.network.handler.ThreadListHandler
import com.nbp.cobblemon_smartphone.network.handler.ThreadPageHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenCobblenavFishingnavHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenCobblenavPokenavHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenCobbledollarsShopHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenCraftingTableHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenPCHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenPokedexHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenRctTrainerCardHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenRctTrainerCardScreenHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenAE2TerminalHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenRefinedStorageGridHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenTomsStorageTerminalHandler
import com.nbp.cobblemon_smartphone.network.handler.OpenWaystonesWarpStoneHandler
import com.nbp.cobblemon_smartphone.network.handler.RequestBiomeListHandler
import com.nbp.cobblemon_smartphone.network.handler.BiomeListResponseHandler
import com.nbp.cobblemon_smartphone.network.handler.RequestGpsSearchHandler
import com.nbp.cobblemon_smartphone.network.handler.GpsSearchResultHandler
import com.nbp.cobblemon_smartphone.network.handler.RequestSpeciesDetailHandler
import com.nbp.cobblemon_smartphone.network.handler.RequestSpeciesListHandler
import com.nbp.cobblemon_smartphone.network.handler.SpeciesListResponseHandler
import com.nbp.cobblemon_smartphone.network.handler.SaveActionOrderHandler
import com.nbp.cobblemon_smartphone.network.handler.SaveHiddenActionsHandler
import com.nbp.cobblemon_smartphone.network.handler.SaveQuickActionsHandler
import com.nbp.cobblemon_smartphone.network.handler.MutePlayerHandler
import com.nbp.cobblemon_smartphone.network.handler.SaveSocialMuteHandler
import com.nbp.cobblemon_smartphone.network.handler.SyncMutedPlayersHandler
import com.nbp.cobblemon_smartphone.network.handler.SyncSocialMuteHandler
import com.nbp.cobblemon_smartphone.network.handler.SpeciesDetailResponseHandler
import com.nbp.cobblemon_smartphone.network.handler.SyncActionOrderHandler
import com.nbp.cobblemon_smartphone.network.handler.SyncDatapackActionsHandler
import com.nbp.cobblemon_smartphone.network.handler.SyncHiddenActionsHandler
import com.nbp.cobblemon_smartphone.network.handler.SyncQuickActionsHandler
import com.nbp.cobblemon_smartphone.network.handler.SocialCapabilitiesHandler
import com.nbp.cobblemon_smartphone.network.handler.SocialMutationResultHandler
import com.nbp.cobblemon_smartphone.network.handler.FeedPostUpdateHandler
import com.nbp.cobblemon_smartphone.network.handler.ThreadSummaryUpdateHandler
import com.nbp.cobblemon_smartphone.network.handler.UploadSocialPhotoHandler
import com.nbp.cobblemon_smartphone.network.handler.RequestSocialPhotoHandler
import com.nbp.cobblemon_smartphone.network.handler.SocialPhotoUploadResultHandler
import com.nbp.cobblemon_smartphone.network.handler.SocialPhotoChunkHandler
import com.nbp.cobblemon_smartphone.network.handler.server.OpenEnderChestHandler
import com.nbp.cobblemon_smartphone.network.packet.CallActionPacket
import com.nbp.cobblemon_smartphone.network.packet.CallOfflinePacket
import com.nbp.cobblemon_smartphone.network.packet.CallStatePacket
import com.nbp.cobblemon_smartphone.network.packet.CreatePostPacket
import com.nbp.cobblemon_smartphone.network.packet.DeletePostPacket
import com.nbp.cobblemon_smartphone.network.packet.ExecuteDatapackActionPacket
import com.nbp.cobblemon_smartphone.network.packet.FeedPagePacket
import com.nbp.cobblemon_smartphone.network.packet.HealPokemonPacket
import com.nbp.cobblemon_smartphone.network.packet.LikePostPacket
import com.nbp.cobblemon_smartphone.network.packet.MarkThreadReadPacket
import com.nbp.cobblemon_smartphone.network.packet.NewDmPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestFeedPagePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestThreadListPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestThreadPagePacket
import com.nbp.cobblemon_smartphone.network.packet.SendDmPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncUnreadPacket
import com.nbp.cobblemon_smartphone.network.packet.ThreadListPacket
import com.nbp.cobblemon_smartphone.network.packet.ThreadPagePacket
import com.nbp.cobblemon_smartphone.network.packet.OpenCobblenavFishingnavPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenCobblenavPokenavPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenCobbledollarsShopPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenCraftingTablePacket
import com.nbp.cobblemon_smartphone.network.packet.OpenEnderChestPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenPCPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenPokedexPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenRctTrainerCardPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenRctTrainerCardScreenPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenAE2TerminalPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenRefinedStorageGridPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenTomsStorageTerminalPacket
import com.nbp.cobblemon_smartphone.network.packet.OpenWaystonesWarpStonePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestBiomeListPacket
import com.nbp.cobblemon_smartphone.network.packet.BiomeListResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.RequestGpsSearchPacket
import com.nbp.cobblemon_smartphone.network.packet.GpsSearchResultPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestSpeciesDetailPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestSpeciesListPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveActionOrderPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveHiddenActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveQuickActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.MutePlayerPacket
import com.nbp.cobblemon_smartphone.network.packet.SaveSocialMutePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncMutedPlayersPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncSocialMutePacket
import com.nbp.cobblemon_smartphone.network.packet.SpeciesDetailResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.SpeciesListResponsePacket
import com.nbp.cobblemon_smartphone.network.packet.SyncActionOrderPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncDatapackActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncHiddenActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SyncQuickActionsPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialCapabilitiesPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialMutationResultPacket
import com.nbp.cobblemon_smartphone.network.packet.FeedPostUpdatePacket
import com.nbp.cobblemon_smartphone.network.packet.ThreadSummaryUpdatePacket
import com.nbp.cobblemon_smartphone.network.packet.UploadSocialPhotoPacket
import com.nbp.cobblemon_smartphone.network.packet.RequestSocialPhotoPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialPhotoUploadResultPacket
import com.nbp.cobblemon_smartphone.network.packet.SocialPhotoChunkPacket

object CobblemonSmartphoneNetwork {
    val s2cPayloads = generateS2CPacketInfoList()
    val c2sPayloads = generateC2SPacketInfoList()

    private fun generateS2CPacketInfoList(): List<PacketRegisterInfo<*>> {
        val list = mutableListOf<PacketRegisterInfo<*>>()

        list.add(
            PacketRegisterInfo(
                SyncDatapackActionsPacket.ID,
                SyncDatapackActionsPacket::decode,
                SyncDatapackActionsHandler
            )
        )
        list.add(PacketRegisterInfo(SyncActionOrderPacket.ID, SyncActionOrderPacket::decode, SyncActionOrderHandler))
        list.add(
            PacketRegisterInfo(
                SyncHiddenActionsPacket.ID,
                SyncHiddenActionsPacket::decode,
                SyncHiddenActionsHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                SpeciesDetailResponsePacket.ID,
                SpeciesDetailResponsePacket::decode,
                SpeciesDetailResponseHandler
            )
        )
        list.add(PacketRegisterInfo(SpeciesListResponsePacket.ID, SpeciesListResponsePacket::decode, SpeciesListResponseHandler))
        list.add(
            PacketRegisterInfo(
                BiomeListResponsePacket.ID,
                BiomeListResponsePacket::decode,
                BiomeListResponseHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                GpsSearchResultPacket.ID,
                GpsSearchResultPacket::decode,
                GpsSearchResultHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                SyncQuickActionsPacket.ID,
                SyncQuickActionsPacket::decode,
                SyncQuickActionsHandler
            )
        )
        list.add(PacketRegisterInfo(FeedPagePacket.ID, FeedPagePacket::decode, FeedPageHandler))
        list.add(PacketRegisterInfo(ThreadListPacket.ID, ThreadListPacket::decode, ThreadListHandler))
        list.add(PacketRegisterInfo(ThreadPagePacket.ID, ThreadPagePacket::decode, ThreadPageHandler))
        list.add(PacketRegisterInfo(NewDmPacket.ID, NewDmPacket::decode, NewDmHandler))
        list.add(PacketRegisterInfo(SyncUnreadPacket.ID, SyncUnreadPacket::decode, SyncUnreadHandler))
        list.add(
            PacketRegisterInfo(
                OpenRctTrainerCardScreenPacket.ID,
                OpenRctTrainerCardScreenPacket::decode,
                OpenRctTrainerCardScreenHandler
            )
        )
        list.add(PacketRegisterInfo(CallStatePacket.ID, CallStatePacket::decode, CallStateHandler))
        list.add(PacketRegisterInfo(CallOfflinePacket.ID, CallOfflinePacket::decode, CallOfflineHandler))
        list.add(PacketRegisterInfo(SocialCapabilitiesPacket.ID, SocialCapabilitiesPacket::decode, SocialCapabilitiesHandler))
        list.add(PacketRegisterInfo(SocialMutationResultPacket.ID, SocialMutationResultPacket::decode, SocialMutationResultHandler))
        list.add(PacketRegisterInfo(FeedPostUpdatePacket.ID, FeedPostUpdatePacket::decode, FeedPostUpdateHandler))
        list.add(PacketRegisterInfo(ThreadSummaryUpdatePacket.ID, ThreadSummaryUpdatePacket::decode, ThreadSummaryUpdateHandler))
        list.add(PacketRegisterInfo(SocialPhotoUploadResultPacket.ID, SocialPhotoUploadResultPacket::decode, SocialPhotoUploadResultHandler))
        list.add(PacketRegisterInfo(SocialPhotoChunkPacket.ID, SocialPhotoChunkPacket::decode, SocialPhotoChunkHandler))
        list.add(PacketRegisterInfo(SyncSocialMutePacket.ID, SyncSocialMutePacket::decode, SyncSocialMuteHandler))
        list.add(
            PacketRegisterInfo(
                SyncMutedPlayersPacket.ID,
                SyncMutedPlayersPacket::decode,
                SyncMutedPlayersHandler
            )
        )

        return list
    }

    private fun generateC2SPacketInfoList(): List<PacketRegisterInfo<*>> {
        val list = mutableListOf<PacketRegisterInfo<*>>()

        list.add(PacketRegisterInfo(HealPokemonPacket.ID, HealPokemonPacket::decode, HealPokemonHandler))
        list.add(PacketRegisterInfo(OpenPCPacket.ID, OpenPCPacket::decode, OpenPCHandler))
        list.add(PacketRegisterInfo(OpenEnderChestPacket.ID, OpenEnderChestPacket::decode, OpenEnderChestHandler))
        list.add(
            PacketRegisterInfo(
                OpenCobblenavPokenavPacket.ID,
                OpenCobblenavPokenavPacket::decode,
                OpenCobblenavPokenavHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenCobblenavFishingnavPacket.ID,
                OpenCobblenavFishingnavPacket::decode,
                OpenCobblenavFishingnavHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenCobbledollarsShopPacket.ID,
                OpenCobbledollarsShopPacket::decode,
                OpenCobbledollarsShopHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenWaystonesWarpStonePacket.ID,
                OpenWaystonesWarpStonePacket::decode,
                OpenWaystonesWarpStoneHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenTomsStorageTerminalPacket.ID,
                OpenTomsStorageTerminalPacket::decode,
                OpenTomsStorageTerminalHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenRefinedStorageGridPacket.ID,
                OpenRefinedStorageGridPacket::decode,
                OpenRefinedStorageGridHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenAE2TerminalPacket.ID,
                OpenAE2TerminalPacket::decode,
                OpenAE2TerminalHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenRctTrainerCardPacket.ID,
                OpenRctTrainerCardPacket::decode,
                OpenRctTrainerCardHandler
            )
        )
        list.add(PacketRegisterInfo(OpenPokedexPacket.ID, OpenPokedexPacket::decode, OpenPokedexHandler))
        list.add(
            PacketRegisterInfo(
                ExecuteDatapackActionPacket.ID,
                ExecuteDatapackActionPacket::decode,
                ExecuteDatapackActionHandler
            )
        )
        list.add(PacketRegisterInfo(SaveActionOrderPacket.ID, SaveActionOrderPacket::decode, SaveActionOrderHandler))
        list.add(
            PacketRegisterInfo(
                SaveHiddenActionsPacket.ID,
                SaveHiddenActionsPacket::decode,
                SaveHiddenActionsHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                OpenCraftingTablePacket.ID,
                OpenCraftingTablePacket::decode,
                OpenCraftingTableHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                RequestSpeciesDetailPacket.ID,
                RequestSpeciesDetailPacket::decode,
                RequestSpeciesDetailHandler
            )
        )
        list.add(PacketRegisterInfo(RequestSpeciesListPacket.ID, RequestSpeciesListPacket::decode, RequestSpeciesListHandler))
        list.add(
            PacketRegisterInfo(
                RequestBiomeListPacket.ID,
                RequestBiomeListPacket::decode,
                RequestBiomeListHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                RequestGpsSearchPacket.ID,
                RequestGpsSearchPacket::decode,
                RequestGpsSearchHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                SaveQuickActionsPacket.ID,
                SaveQuickActionsPacket::decode,
                SaveQuickActionsHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                RequestFeedPagePacket.ID,
                RequestFeedPagePacket::decode,
                RequestFeedPageHandler
            )
        )
        list.add(PacketRegisterInfo(CreatePostPacket.ID, CreatePostPacket::decode, CreatePostHandler))
        list.add(PacketRegisterInfo(UploadSocialPhotoPacket.ID, UploadSocialPhotoPacket::decode, UploadSocialPhotoHandler))
        list.add(PacketRegisterInfo(RequestSocialPhotoPacket.ID, RequestSocialPhotoPacket::decode, RequestSocialPhotoHandler))
        list.add(PacketRegisterInfo(LikePostPacket.ID, LikePostPacket::decode, LikePostHandler))
        list.add(PacketRegisterInfo(DeletePostPacket.ID, DeletePostPacket::decode, DeletePostHandler))
        list.add(
            PacketRegisterInfo(
                RequestThreadListPacket.ID,
                RequestThreadListPacket::decode,
                RequestThreadListHandler
            )
        )
        list.add(
            PacketRegisterInfo(
                RequestThreadPagePacket.ID,
                RequestThreadPagePacket::decode,
                RequestThreadPageHandler
            )
        )
        list.add(PacketRegisterInfo(SendDmPacket.ID, SendDmPacket::decode, SendDmHandler))
        list.add(
            PacketRegisterInfo(
                MarkThreadReadPacket.ID,
                MarkThreadReadPacket::decode,
                MarkThreadReadHandler
            )
        )
        list.add(PacketRegisterInfo(CallActionPacket.ID, CallActionPacket::decode, CallActionHandler))
        list.add(PacketRegisterInfo(SaveSocialMutePacket.ID, SaveSocialMutePacket::decode, SaveSocialMuteHandler))
        list.add(PacketRegisterInfo(MutePlayerPacket.ID, MutePlayerPacket::decode, MutePlayerHandler))

        return list
    }
}
