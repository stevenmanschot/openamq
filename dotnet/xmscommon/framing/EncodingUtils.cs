using System;
using System.Text;
using jpmorgan.mina.common;
using log4net;

namespace OpenAMQ.Framing
{
    public static class EncodingUtils
    {
        private static readonly Encoding DEFAULT_ENCODER = Encoding.ASCII;
        
        public static ushort EncodedShortStringLength(string s)
        {
            if (s == null)
            {
                return 1;
            }
            else
            {
                return (ushort)(1 + s.Length);
            }
        }

        public static uint EncodedLongStringLength(string s)
        {
            if (s == null)
            {
                return 4;
            }
            else
            {
                return (uint)(4 + s.Length);
            }
        }

        public static int EncodedLongstrLength(byte[] bytes)
        {
            if (bytes == null)
            {
                return 4;
            }
            else
            {
                return 4 + bytes.Length;
            }
        }

        public static uint EncodedFieldTableLength(FieldTable table)
        {
            if (table == null)
            {
                // size is encoded as 4 octets
                return 4;
            }
            else
            {
                // size of the table plus 4 octets for the size
                return table.EncodedSize + 4;
            }
        }

        public static void WriteShortStringBytes(ByteBuffer buffer, string s)
        {
            if (s != null)
            {
                //try
                //{
                    //final byte[] encodedString = s.getBytes(STRING_ENCODING);
                byte[] encodedString;
                lock (DEFAULT_ENCODER)
                {
                    encodedString = DEFAULT_ENCODER.GetBytes(s);
                }
                // TODO: check length fits in an unsigned byte
                buffer.Put((byte) encodedString.Length);
                buffer.Put(encodedString);
                
            }
            else
            {
                // really writing out unsigned byte
                buffer.Put((byte) 0);
            }
        }

        public static void WriteLongStringBytes(ByteBuffer buffer, string s)
        {
            if (!(s == null || s.Length <= 0xFFFE))
            {
                throw new ArgumentException("String too long");
            }
            if (s != null)
            {
                buffer.Put((uint)s.Length);
                byte[] encodedString = null;
                lock (DEFAULT_ENCODER)
                {
                    encodedString = DEFAULT_ENCODER.GetBytes(s);
                }
                buffer.Put(encodedString);
            }            
            else
            {
                buffer.Put((uint) 0);
            }
        }

        public static void WriteFieldTableBytes(ByteBuffer buffer, FieldTable table)
        {
            if (table != null)
            {
                table.WriteToBuffer(buffer);
            }
            else
            {
                buffer.Put((uint) 0);
            }
        }

        public static void WriteBooleans(ByteBuffer buffer, bool[] values)
        {
            byte packedValue = 0;
            for (int i = 0; i < values.Length; i++)
            {
                if (values[i])
                {
                    packedValue = (byte) (packedValue | (1 << i));
                }
            }

            buffer.Put(packedValue);
        }

        public static void WriteLongstr(ByteBuffer buffer, byte[] data)
        {
            if (data != null)
            {
                buffer.Put((uint) data.Length);
                buffer.Put(data);
            }
            else
            {
                buffer.Put((uint) 0);
            }
        }

        public static bool[] ReadBooleans(ByteBuffer buffer)
        {
            byte packedValue = buffer.Get();
            bool[] result = new bool[8];

            for (int i = 0; i < 8; i++)
            {
                result[i] = ((packedValue & (1 << i)) != 0);
            }
            return result;
        }

        /// <summary>
        /// Reads the field table uaing the data in the specified buffer
        /// </summary>
        /// <param name="buffer">The buffer to read from.</param>
        /// <returns>a populated field table</returns>
        /// <exception cref="AMQFrameDecodingException">if the buffer does not contain a decodable field table</exception>
        public static FieldTable ReadFieldTable(ByteBuffer buffer)
        {
            uint length = buffer.GetUnsignedInt();
            if (length == 0)
            {
                return null;
            }
            else
            {
                return new FieldTable(buffer, length);
            }
        }

        /// <summary>
        /// Read a short string from the buffer
        /// </summary>
        /// <param name="buffer">The buffer to read from.</param>
        /// <returns>a string</returns>
        /// <exception cref="AMQFrameDecodingException">if the buffer does not contain a decodable short string</exception>
        public static string ReadShortString(ByteBuffer buffer) 
        {
            byte length = buffer.Get();
            if (length == 0)
            {
                return null;
            }
            else
            {
                lock (DEFAULT_ENCODER)
                {
                    return buffer.GetString(length, DEFAULT_ENCODER);                    
                }                
            }
        }

        public static string ReadLongString(ByteBuffer buffer)
        {
            uint length = buffer.GetUnsignedInt();
            if (length == 0)
            {
                return null;
            }
            else
            {                             
                lock (DEFAULT_ENCODER)
                {
                    return buffer.GetString(length, DEFAULT_ENCODER);
                }                
            }
        }

        public static byte[] ReadLongstr(ByteBuffer buffer)
        {
            uint length = buffer.GetUnsignedInt();
            if (length == 0)
            {
                return null;
            }
            else
            {
                byte[] result = new byte[length];
                buffer.Get(result);
                return result;
            }
        }
    }

}