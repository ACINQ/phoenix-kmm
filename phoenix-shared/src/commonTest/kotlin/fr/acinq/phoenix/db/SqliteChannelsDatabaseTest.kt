/*
 * Copyright 2020 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.db


import com.squareup.sqldelight.db.SqlDriver
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.lightning.CltvExpiry
import fr.acinq.lightning.channel.Normal
import fr.acinq.lightning.channel.WaitForFundingConfirmed
import fr.acinq.lightning.serialization.Serialization
import fr.acinq.phoenix.TestConstants
import fr.acinq.phoenix.runTest
import fr.acinq.secp256k1.Hex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SqliteChannelsDatabaseTest {
    private val db = SqliteChannelsDb(testChannelsDriver(), TestConstants.Bob.nodeParams)
    private val db2 = SqlitePaymentsDb(testPaymentsDriver())


    @ExperimentalCoroutinesApi
    @Test
    fun basic() = runTest {

        // this is a old channel formal (cf fr.acinq.lightning.serialization.CompatibilityTestsCommon#`read compatibility test data`)
        val wait_for_funding_confirmed_blob = Hex.decode(
            "2d0cef30160b00f8c206f1e0618f597b774b67338a1a52f4e1d13616a4032719bfb41817e7272b215afcc9d68e64fa46c94fa75f5b1c374832891a7ce4d326ec6cbd7cc3a9c876a9df0254b1306d02ef4755cf6a292dd643ee90cffa111b4a20b46c060d985ebb7618bf5c1d1d05b6c2e6535d3bf0f25e2bbae396dc2bca66e390d18ef46965d6986835cfa602728763adf74cde41924c609b41fef839b3eba6b514b5c70bbc5c58159122381407cce35643143aff68bef046a13a208cc4ee738b2b943e85d85004d94b014609c1876a48694e9b4ad92ceaa40094901c183103af1a25a35d6c38128921d53dfac1ed121ca6acd3dc66bdb90603d2f6cfb790303afcea3dd4b5a3f80c1ddb8abcac2fad767c91bd1f072bbf6269a79d210e2a8f16cd030ffc297c3ad746c21969c48a602acbc5b05aaf3875f91e0186428128f81a73bc872d144d36840c5dc51378911c06638d3d4b85193ce7e72a656f2592c862910b9e34f79e6173996e87b7a62f8637d62407da219823f6d5c3b13f30c0995f7a6eac5febfd16d4c7b8c389beef57ee9e44b731d25a3a9fb3fbfdd02884136bed9ee8c975b276c0dd9fb1b37dc4ce8a3c4fa674a6724384c5616b0c8b86f52bd66b0b5e1cc6d8c8e4fe0f818a4e5d9bc04f1fee5f2b63af5eee8340adea0654058058fcd16b6ef4d2c8154e2df4fa142fe289f26ddb77dc8da98401f4a7d47f8398977292858bc0092e9dfc1d3c1d621938f4d5fd4e36c7637a0773a0f1b546357e330c61fee88dd273e956f45133a765da823f2686b15ceadc84048a4dbaf0a9e83e6ff06bff586e6734b3a4e4a8511369c07f88f1efd548c1fff2d1d0aecfe1504232548cfae442249fc5d066aa93e442b511de05eb2be87595922be69e655c1048889ff10b1fc5047e7537f5db225a95049db32f885016e32699766b1e111040849122d2c0ff6600a349292a27022b3d1964bb5d8050362f3bf042641872e8d0dfb39c94bcf9068c190450ca4126efca8bbd926a8be65c9d07e4b2d19a226596a50fec795fc0b715a9cc324a13e15c76bdea9a0e4af8689dc95410638aa4b655f7bdac268284ef74c824d9626953ab202fa185715317f7621d602aae5e186d6b4820663c418d7374a2e8d378a306861ce1d775a4a8c5c06784abc084d45b001289b06bb39053e7777b6d691af5affb5548aa703715d41435a43e824beae0051ebf0189d1228f7e6065821925bef16de743a91b74396c39318611bc28484a7e96cf9066113ea27df045b09dfa89c880a08775cd209c6b6f543c742bd07e61e52b4aa41899a705646a790d2929db5e8610bdf75511f8b1a6d48cf3b691a0cf6a1ef6c92670b2c6dd97ad94077dc5408100244382b89bc452a17f5103aed6500a2a54bbd88e0ff08d9666270f0f3e015e11f6f34c177f7af4adfd04f8978f18a524cff48c70f1854f82f85049c1f9ed24155284dac43dae45fdb49aff52e9eb11af884044da9a312bc158142cc6e7aaad028e8260da39eab30483a7763cef83d73f12a98979f536a46deeda25b89383e963711349cc14e4defb2847bb0c61df705f465f0608dcebdf8bc1306d4910bfe438ef10b718fd0329f4f85cbceb37d853ff743a848b010b1e50b44edc47334600e664acebd48bd83cf8394be1c168c0cc4da41da83f13c6b879392fe035631cc038b8eda26c0ba07802480774ac013368b741634fd29784c38acb9fd5f17f84d62ae76579b392b97f200dcd73efaa7790a1943fb99d77589ab5c6598bf43bb9bd034179b01f869b5d430e4aec014f619edadb8fb8c1396f18f71b1feb200ee93a8ee70ef450e9f5bf9eb5d42987a4d34b698c9bf6f1c0de914fd3b896f523941a3929ea6e4654a33cbeb05babecdf0e5dfe003338caad556af72c9efa990ab802f8a3cdf15f05a6dd0c884eb58ce3d06958e57555a30fc8954edff727568c2a63b51dce9e03633211f7feb5b80ee4a595d6b10d97acb2dc0c5cc8a6153e2710d774ed50ebcf85556847969698a74c1b4a030774bb7edd355cca7d5de68c0a5b3c5f3fae02a92c0dfb052f7c9ffa970d9fb3bf79f088eddadeefbcfdaca8099cfaf3ed0a111efb962a5b893279ed5f624b36f887c6b244352dc946d112bed31a538022d27e1334d51bf4a4972e6fa5eec28094492bd5b5cb5a5665664f72d39337800dea153813f16518aaee105356aa9a401d8b65b4fbb951a71c33206bb9642f6d315d2cdd12287fbdc7ac7442ab75a2ae2506a0a6458377eb505acbcbdf4a5d8194ad44deb118baa21816609ee26b2df267796e22b3e531891a2596ecd696554c6d2110bf13f5241e2daa71a56fd124d4cb8b78c35d67722ac64d676b39bdad7490ce576a90c7682bfe161fe19fa95d742031777b4c80443f22f6d620f0256449555e72e5b382c43d739342adacdf6ce6501cd9b5b57a8a504aa4db64058ee0ca72f32b62900e74be6f855ebac216f54a6b9f1cd22cf03db900488b1dd5defc457fd06c670ad8ee9e7b5ad2a9c321b447d548ce00f8eaed63a8345de12eeb4525f21d8c5ef66bc3fde74a0bfb72369f49a92e6560a51c67ee4d1b1ce307daca36939cb35fad382a3ef6f4bd8315aa82e00467784740815cc89827926a7b06ed47c344a9e6c0077affe80dcc5c57ad12c53afc45064202011f94ac57e3073e901e168514a1fb5e35af3ce1fbd9aa04ee434d18bd784cdd9ad8fe2091790a70e39059a51a318e1dc0258663485df58f48b7474f348ccea202d4814c8c077497923667a84d2d5a809e6235486354bd36843f0f5c5cd915b4d0900482cf8912f595a064278eb83d3af361a8ff57cb1455e40142ff3ecb606154c8ab1eee031cef2144984bf9a6a0cdb751a9ccb8bcbc8c0ce2e2b339ece889a037a0e1745b22ec7b8779d18260357fb2f311e73234651bdb3fc651a8198d3310f815c9b2b02a55604436bf899863c84eb7f6d3a420323b92e4f3a3277c6a7ce4fccdc0f564981d14f44543682d93f6208ecd6188a119f95dd8507eeac3c6d058151e1a7f4f18a46a204f6453aaa0a2108089f4c7b12873a9ed9e262c54ff53df4df819b7929e5d528bc44af5b6f34b26d49bda7b4b849f08e09f1d5ae1338281e4717a0c7f150d419028c5a215811b3421644ecb5de2c1efd95d0bc74339a47a3c31e3ecfa873d9382f2f8cc4c566db19a0edb2ee36e6e7d8dc0aeae4f06cf30525c89fbfa844b6b7494d23fe3ad152f8e6da913e9960322d99267904fc8768106d40690c8a90dc01d8514ab1937c96edc16f0502fe81491b5c5c95f255d6e07722f5b39d376bd1c7a63339a5c6f6fccd6a0c6a6840723cac35da3b806c0924550461fb5fc93f30cee4d61cc167a256bd08d78c365299c53aecc8855e4c6ffffc33eb077427785b4b6a18368a238e496630a5c9ab16ff5aa4e84f86fd4d8f3fb13ca3903302f7adcfdcccc45ea945cb9636cb2d1bfe505c1270c0f747ac90024c3c958684c3c087bd54e762f6ea7466283d36cd879a5494dd382c37c61c418576a8635d784c8cdff9b399ea3b02347aab6fe5bc2ee96ab76199d81ff8e532feb7aa504567fd4470f0f8ec11d591ca15f53229c24a1a14bb4544f07a1f19f36af6617102a37836dbad9961e20f026d8e07001bdd09a3afee156ef68d92c938feb92f74656d5eef63783087989ee4f16ae7eaabab0714b5e272263c21ecd294eddad9671c7bd11ce44f23e5b0647fe64d1f3c734bb59a7dcf366df1a899018a2fa51a225a7112bd05fe3c2fb0e24eedb4922a92fb32a9361f9f65d67c765abd2c2a1ef25d4d615bcb9d773a21de944f083718f3cd0eede1826580433706816bb7bbd22fae3fde0b6eeabc8da690e8609034e973c99ca1b3f70f4a2b8f8343babbe1322c947752cdac45701b25c43e8cfde6d939801c2144dd326cec2890955992e5b0b9359390f14ac20801bce083302e3aaeec74371955a6cebb127223dcb3246b79f10a9e037f44c9610ae803f852100487e0f6a2fc52298b05dab2f3d742a43e61316d07de5851183560ecc79504f4747fdf6f64aaa48c97ed29e070a903da9e12176e125c6cbdc14150711a93569cb687477d3265ca281850e8b3dd39470eaf70fa5910785ae99aa3f3e6b90edbb7a03f634f59f1346f5668f1a329cabdc0ba2fc5258da0e761047b5e87a308ca6f48f38a806699efc3b8beb1e1b35e89b8ddfa53b8e466d0b3e82fac0ebdc78a86a6ac823b6f38b568dfaa1e3114b544bec784d13c43d33fa35c8580fac385477a39baaa0b79f68a9d38480b5b2ab5bf3cb19bc385fe973986070d6f3a7b2d764edff376d51dc4bc72bfaa17a09"
        )
        val wait_for_funding_confirmed = Serialization.decrypt(TestConstants.Bob.nodeParams.nodePrivateKey, wait_for_funding_confirmed_blob, TestConstants.Bob.nodeParams)
        assertTrue(wait_for_funding_confirmed is WaitForFundingConfirmed)

        val normal_blob = Hex.decode(
            "d9c059fda686c94cfa910b614eda7d7faa13771b7d3d191847457b04d29267503784deedc68cd22e6f8474f1da05fbf23ef4fad7d3fea19bb57e5963613a0a1394867758b6a133299213a5574d642470bf34e5dbcc4b4d75d282406165b77fde104ec5081df589de530d2f20b81dd4b35fd9eb4c670aa68ff80410295f998911bc5ac8d3c2c21c9feb41571661455283f7ea25beb2d9bd95f7682c8316760bbe0cf6d452f8d9ebf8faab1ffff7e275cbc88482bd994214449569e813c4661938eac28e560e2f5e6a8ae31b9e8f7b8aa67eecc1c5369fac1922aa1f93b2853926fe77d1f7d4935b01acef18cf497119601b4cc8a93192dcd3e9b637159b6a9989ccc00fd451ca8b99231cea3ed6f8ec4f6996135ab2765a40f15f30d5e01276586585351215d82e6b903fbe4a7d72a2e355ff328e893072058d4bf971e91d21d9cc9741bbdfa9a3f85e4c643a005ebebbee3b6c95a074b66b8ef521c3a6ef1500d993679ddce3bfcc306a730be5e38e746e3105bea34720fd82ec72204a5d9d0da3e99ce33b51cdcbef6096067085266ed723b6dd45a89bc9bb228e20627336dabcd8ca08167d5cd0d774f49aaf058843e147c8a03427bb0d2a85235d66103cd98684606da8f22e8203bd02d28170f5c05838f277c234f4ed2eb880f723d29ec5a41b13d21b483c6be0ac85fee649b258dffe65b56df378e37017abb8a3f8a0fb2dbef36662058dc33adac6b72ca902f2002caf28f72b454b1b9f65f52aff2427326ad4cc7eb0bbf54144a11381653c8fda5aa26fcba999f70ed75d7ae1e47f83c8266c952b36e583ff34d2129ea9c3aac9e41da8cce0e3b090aee0c8c21f823c8f4ad9d95bbecfb15db459a892a930359e5f1ad7a3ffbd0713230ba92d06236c5ab16e8b6a90108817c7b8349c3bff35f5063a5d2da07ec00fcb4451d49b51f28da688cc50d012ce827c46556cf68f50ad8ddb369b6bad4346f36ae5c640b59f1a290f73ee43b99dbf6d03e85911faa5f1c198f733188dd83ba3d44b484821c13b3690fdc080db478c79ff43b91f6f261d7510021a4e7502d1a247964695b90b78c088ba6280bd19f05954e08a878f2884e1aa23b3c37b60e488fa67650635d51fac6bca0a5c3c8ab081d5e189c107a41f6d9d43483d42e5f13b51c3f5552d3084991b695ba7dfa9cf10858b9516279de557162c52017411bd4e5f1b496bea23f5577447ecf5702978f6262a0fc5f5bfc25dd11b5c432e2f409fe0fa9284ef5ee1eb90c6a5261eaf816b9db7242e8bf2be68c41ffec8bb704354416be2d0c12f07444e69134d498881e78728c444ec56721c0c3cb37892d99bcf83bf9481485e902ce9de42183896d08c0067ae52e335a999290f6e8d8235e338eb8c319b206e52c2468fabcac11eed568486f8e230974d60cd526060d84df72a3cbe33ab58491267d0092738b3bd44ceb1deb8ee94daff54fa55e4b6fcc32a50e94eb87240b855bc8d785ae9305b864e5c97bf6460173c8e62f8065278b0555cc8e88a983dd1b74c2c43098be4ac9cb1c1703bf0354f8c09fea7fbc7b049c04cdc5be08797410c979123e7dc42166e37481f5957c28cd77d7630dbedb4908dac588ac2d881fd55b5ebc2f44ca9789c3c979fe72e5d8ae9aaa08e3b5690bb5ac24b659055a20b600a7a4ace9600283bcb98b41b6235e020d6f0c84b44276f9b7875c62945db8b948bdf8b012c8d12544b458b0051b19e8157845f01756724d2a05d10d7017c4e540275f5558eac4decb3697d1509c17f07c61891ce32b7e993729231812abd429540b2e6c003d4d6b89e90c883617a2c52d05dbeb0d17db3be69182abc8d0f229e0a8ddb182f0401cf2b71bca666901d2461fc86dc8ce036ccb59e4349f69481fb2db4abd98e3933156f0da1463878fa064e9baad95ae037b7cdf43376c8b0a31be746781af74e21270162384fbca6a759f7b531f431db781915f9669b5f143932b43682dcfcfe7d350387fd1535b993bb40ad100e11bdd905cae0602ccbe6a9c78258b2096dce6efc48bc5be881814c71b31905274d79a374c49ac58fa935bbf498055d82aef88ac2ebf1ec1b1b0d2c65581024095433be600b1fda04b4d1b0631c8b8b180268c738fa992675f4b83e33648eb37e3f9534b2716c9bd05b6b3fc2303ab667616280ce538bfb18a3f48d7d1a88d407ce5db5174b313445115b69f40e140d5f657e03316455ec1df08eaeba3ae80033adc7a747c1d5625f9ac4aed3961a441b4a5f3e79e0fbeebdfc48366c9bfa225c69869d2f4c24063e18caeebaaf89b4ae2ade1129f850b4b9da37c04b6b3c68fccecb31bec1511c5bd30f5b4211c77220b49aeedd8a015003fbad79de82804434f53f632bdfa575ddedfbd516f6dcfc75b1f801a6937adf0cc7911806842bc36e5a0e90dd4b8e24a25da37e1fad42ed84a3f84602f403333d0e99d9cec649aec39e25c0cf2da3941aa5abc4bc8c99bdb3690c3ad81e99c531a1f968e8362e17bd5a71303329542d7565e4c5f94879c563605b23ff0236212d60c1f82de3933091f2e60a15151440bc426253a8f0cfeebdd4b87d165032d3cb1325824e5056215024a6788ee49302afaf67a03d66853e908c5b09bbde4ae22610bb2d38c5dc1d1203f09e21f8e3929938bdbf1ffcc35c8ce0ab40236366a7b246e7785a506fa5f9d4e73f5eb8d66f9f7d14703d05c3ce7eb5074450d1967cbab3e5362a4844b727cb8300e588d0695af3142291cecf291c5909649ebd0601e33f9faf5c4c75ea4568cfaf05ddb6395d5f5d5d84ae9a2eff944ab17068dee04a431136f544644731172e13454c166a304ea088adcc0b27373946fe7c1b9025d4262a0651756b31fbf91cdef87a5c2c2d2d52726dfb50339ce1d9e2c2ba6a49eed2837ae0cb855ffd8eea496dae92e02416414e82d41c9cc29013302efac9dd297985e39e40b80e088eab7457023de984c8dabaa72bebc0be3f84e33d554e94ce6badd57b8e684e2847c4dc7f322c6902fcaecd49313a8762e81f2c6c2a55bdcb1cb17a1051b508708444b0b15bccc569cb215e7e7fe69d52a3b8904918c61823660defe348508045af58be33acceafa435b617e32e9d0bb801f0cb6a8d0db1c8f2a8d7b4fea8b52077d33f9456f62cfaeb66965637f2c05fb4d71319105e284ff2fdab75aa8e04785503de3bf30e4e11c2f7aacc7d1b668926ee14d9e5912fab813832f9dcc49a7da8ae845c20b95fa97898ada959143901e4a127b945bd9ce26017421d9579e536a111d8f632021aed71fbd2df89b344b3af5673f47bdbd0f809ad37c30230c9611dd0020e409ac780f55e7e8f71980a029bb8aa94328cbc7561382e0942fde98bc21566c48b8838f84298d875a61c192cf7f31546d71866207a080353f7e25b820bb4d18f44f14dfbd7447664b0a39f4767e46b0b7bb5dba731ae8dc90d3f0df57574a1b6af823325af6b96afa0e0c6da2e40e54e67122ce8ebca3fb81c11d35bd13163aeef05b8acd5c568db5a844658b2422fcb03bdeac4e4084c9b699ec8180cadb8e42a3665f52bc12b77b34108a38f7d2c3253ed568e49da98612d9374147ffeb79d2082338501975803aaab49b5f1797aef0c924eeaded8bd01b956331938f3538b0bc9a30e8a56e3261c722cd285eb6d23a14c79635c218d06b19ab2132c036d5d82e656651b2b1d0106182e77464ef6e17da63e9fa211d27aa097137d026b9700e7a0de21aa110462121ee91485ebec8d2e0625bfe29ea1b9fae0a7e51b87c32f520601e116af37c727cb4016ee4eda3bdfb7af4a470bd792c93ad8bad35b32849f7da15fd558d63dac00e0d42bf787f6c1f03eb7b2e36cb8b115bc8a9ca01b5a5f746f90bae8e1849e463518eacb48719d74e020f2b1b1fbe8de2df454dd29b3ff21a502d7da0610a051a2f810da001e03f3722bf80d0cb41891dcfae3c4761e43bc40f0b55c2b4409d89fdc76fb1cd0dbc33e904f7ee3cadf22db145d7cd69b62cc76ce8d1d62f955238d9588f41da429da86ac7f69324bb41878bd6f2abacb3d4b77560c160901f56dffdc0e1c1dfde3af671473c4dbc98f8dc99b4e1be302ed8bf5a4ba519215e5236d83789fc7db3d2d63184eb152bb578c93cd454e9c4fe53e2c395267491fee5cf297c8233501eaef378df3ba032d783fb1265938dc501fbfa8340a5b64ef290ece2ab6a72229f5b17641b7ba09bf041d0a8503c0cebca8a9a099d1d7b1271b9f559d951d1ede059afb97619e63ad174f7dc9b611a43f6ed67602b2e6849f65b1e0f25c9497a22800bc77a3845566daf8ba7ad2cf7e0f1a316cbac4430769ade16e1b77805a6804763e109b370f7939dce22277ea41a514bc6182fc7001e278c14e1a85496b"
        )
        val normal = Serialization.decrypt(TestConstants.Bob.nodeParams.nodePrivateKey, normal_blob, TestConstants.Bob.nodeParams)
        assertTrue(normal is Normal)

        val commitNumber = 42L
        val paymentHash1 = ByteVector32.Zeroes
        val cltvExpiry1 = CltvExpiry(123)
        val paymentHash2 = ByteVector32.One
        val cltvExpiry2 = CltvExpiry(456)

        // cannot add htlc if no channels
        assertFails { db.addHtlcInfo(normal.channelId, commitNumber, paymentHash1, cltvExpiry1) }
        // db is empty
        assertEquals(0, db.listLocalChannels().size)
        // can add 1 channel
        db.addOrUpdateChannel(normal)
        assertEquals(1, db.listLocalChannels().size)
        // can add the same channel again, no changes
        db.addOrUpdateChannel(normal)
        assertEquals(listOf(normal), db.listLocalChannels())

        // we can have many channels
        db.addOrUpdateChannel(wait_for_funding_confirmed)
        val channels = db.listLocalChannels()
        assertEquals(2, channels.size)
        assertTrue { channels.contains(normal) }
        assertTrue { channels.contains(wait_for_funding_confirmed) }

        // can remove channel
        db.removeChannel(wait_for_funding_confirmed.channelId)
        assertEquals(listOf(normal), db.listLocalChannels())

        // channel is pristine
        assertEquals(listOf(), db.listHtlcInfos(normal.channelId, commitNumber))

        // add htlc to normal channel
        db.addHtlcInfo(normal.channelId, commitNumber, paymentHash1, cltvExpiry1)
        db.addHtlcInfo(normal.channelId, commitNumber, paymentHash2, cltvExpiry2)
        assertEquals(listOf(Pair(paymentHash1, cltvExpiry1), Pair(paymentHash2, cltvExpiry2)), db.listHtlcInfos(normal.channelId, commitNumber))
        assertEquals(listOf(), db.listHtlcInfos(normal.channelId, commitNumber + 1))

        // remove our last channel, no zombie htlc is left in table htlc_infos
        db.removeChannel(normal.channelId)
        assertEquals(0, db.listLocalChannels().size)
        assertEquals(listOf(), db.listHtlcInfos(normal.channelId, commitNumber))
    }
}

expect fun testChannelsDriver(): SqlDriver